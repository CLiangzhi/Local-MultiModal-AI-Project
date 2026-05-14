package com.example.ai.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

@Service
public class RagService {

    private final EmbeddingModel embeddingModel = new BgeSmallZhQuantizedEmbeddingModel();
    private final Path rootLocation = Paths.get("local_data/knowledge_base");

    // Chunking
    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;

    // Retrieval
    private static final int BROAD_SEARCH_K = 40;
    private static final double HIGH_RELEVANCE_THRESHOLD = 0.45;
    private static final double BROAD_SEARCH_MIN = 0.2;
    private static final int MAX_CONTEXT_CHARS = 20000;

    // Keyword extraction patterns
    private static final Pattern SECTION_PATTERN =
            Pattern.compile("第?\\d+([\\.\\-]\\d+)*[章节节]?|\\d+\\.\\d+(\\.\\d+)?");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("[「「\"\"'](.*?)[」」\"\"']");

    // ======================== Vectorization ========================

    public void ingestDocumentForUser(String userId, Path filePath) {
        try {
            Path userDir = rootLocation.resolve(userId);
            Path storePath = userDir.resolve("vector_store.json");

            InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

            if (Files.exists(userDir)) {
                Files.list(userDir)
                        .filter(f -> !Files.isDirectory(f))
                        .filter(f -> {
                            String n = f.getFileName().toString().toLowerCase();
                            return (n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx"))
                                    && !n.equals("vector_store.json");
                        })
                        .forEach(f -> {
                            try { embedFile(f, store); }
                            catch (Exception e) {
                                System.err.println("[RAG] 跳过 " + f.getFileName() + ": " + e.getMessage());
                            }
                        });
            }

            store.serializeToFile(storePath);
            System.out.println("[RAG] 知识库已重建");

        } catch (Exception e) {
            System.err.println("[RAG Error] 向量化失败: " + e.getMessage());
        }
    }

    private void embedFile(Path filePath, InMemoryEmbeddingStore<TextSegment> store) throws Exception {
        String name = filePath.getFileName().toString().toLowerCase();
        Document document;
        if (name.endsWith(".pdf")) {
            document = loadDocument(filePath, new ApachePdfBoxDocumentParser());
        } else if (name.endsWith(".doc") || name.endsWith(".docx")) {
            document = loadDocument(filePath, new ApachePoiDocumentParser());
        } else return;

        String source = filePath.getFileName().toString();
        dev.langchain4j.data.document.DocumentSplitter splitter =
                dev.langchain4j.data.document.splitter.DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
        List<TextSegment> segments = splitter.split(document);

        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).metadata().put("source", source);
            segments.get(i).metadata().put("chunkIndex", String.valueOf(i));
        }

        store.addAll(embeddingModel.embedAll(segments).content(), segments);
        System.out.println("[RAG] 向量化: " + source + " → " + segments.size() + " 片段");
    }

    // ======================== Delete / Rebuild ========================

    public void deleteFileVectors(String userId, String filename) {
        rebuildFromFiles(userId);
    }

    public void rebuildStore(String userId) {
        rebuildFromFiles(userId);
    }

    private void rebuildFromFiles(String userId) {
        Path userDir = rootLocation.resolve(userId);
        Path storePath = userDir.resolve("vector_store.json");
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        if (Files.exists(userDir)) {
            try {
                Files.list(userDir)
                        .filter(f -> !Files.isDirectory(f))
                        .filter(f -> {
                            String n = f.getFileName().toString().toLowerCase();
                            return (n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx"))
                                    && !n.equals("vector_store.json");
                        })
                        .forEach(f -> {
                            try { embedFile(f, store); }
                            catch (Exception e) {}
                        });
            } catch (Exception e) {}
        }

        try {
            store.serializeToFile(storePath);
            System.out.println("[RAG] 向量库重建完成");
        } catch (Exception e) {
            System.err.println("[RAG] 保存失败: " + e.getMessage());
        }
    }

    // ======================== Retrieval ========================

    public String retrieveContext(String userId, String userQuery) {
        Path storePath = rootLocation.resolve(userId).resolve("vector_store.json");
        if (!Files.exists(storePath)) return "";

        InMemoryEmbeddingStore<TextSegment> store = InMemoryEmbeddingStore.fromFile(storePath);
        dev.langchain4j.data.embedding.Embedding queryVec = embeddingModel.embed(userQuery).content();

        // --- Step 1: Broad semantic search ---
        List<EmbeddingMatch<TextSegment>> broadResults =
                store.findRelevant(queryVec, BROAD_SEARCH_K, BROAD_SEARCH_MIN);

        // --- Step 2: Extract keywords from query for lexical boost ---
        List<String> keywords = extractKeywords(userQuery);

        // --- Step 3: Hybrid scoring ---
        List<ScoredChunk> scored = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();

        for (EmbeddingMatch<TextSegment> match : broadResults) {
            String text = match.embedded().text();
            String fingerprint = text.substring(0, Math.min(40, text.length()));
            if (seenTexts.contains(fingerprint)) continue; // dedup near-identical chunks
            seenTexts.add(fingerprint);

            double score = match.score();
            // Lexical boost: keyword matches increase score
            double keywordBoost = 0;
            for (String kw : keywords) {
                if (text.contains(kw)) {
                    keywordBoost += 0.15; // each keyword match adds 0.15 to score
                }
            }
            double finalScore = Math.min(1.0, score + keywordBoost);

            // Only include if: high semantic relevance OR boosted above threshold
            if (score >= HIGH_RELEVANCE_THRESHOLD || finalScore >= HIGH_RELEVANCE_THRESHOLD) {
                scored.add(new ScoredChunk(match.embedded(), finalScore, score, keywordBoost > 0));
            }
        }

        if (scored.isEmpty()) {
            System.out.println("[RAG] 未找到相关片段");
            if (!keywords.isEmpty()) System.out.println("[RAG] 查询关键词: " + keywords);
            return "";
        }

        // --- Step 4: Sort by (source document, chunk index) ---
        // This groups chunks from the same file together and keeps original document order
        scored.sort(Comparator
                .comparing((ScoredChunk c) -> c.segment.metadata().get("source"))
                .thenComparingInt(c -> {
                    try { return Integer.parseInt(c.segment.metadata().get("chunkIndex")); }
                    catch (Exception e) { return 0; }
                }));

        // --- Step 5: Assemble context (fill up to MAX_CONTEXT_CHARS) ---
        StringBuilder context = new StringBuilder();
        String currentSource = null;
        int totalChars = 0;

        for (ScoredChunk sc : scored) {
            String source = sc.segment.metadata().get("source");
            String text = sc.segment.text();

            if (totalChars + text.length() > MAX_CONTEXT_CHARS) break;

            // Add source header when switching files
            if (!Objects.equals(source, currentSource)) {
                if (context.length() > 0) context.append("\n\n");
                context.append("── 来源: ").append(source).append(" ──\n\n");
                currentSource = source;
            } else {
                context.append("\n...\n\n");
            }

            context.append(text);
            totalChars += text.length();
        }

        // --- Step 6: Debug output ---
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        int kwBoosted = 0;
        for (ScoredChunk sc : scored) {
            sourceCounts.merge(sc.segment.metadata().get("source"), 1, Integer::sum);
            if (sc.keywordBoosted) kwBoosted++;
        }
        System.out.println("[RAG] 查询: " + userQuery.substring(0, Math.min(60, userQuery.length())) + "...");
        System.out.println("[RAG] 命中 " + scored.size() + " 片段 " + sourceCounts
                + " (关键词增强: " + kwBoosted + ")");
        if (!keywords.isEmpty()) System.out.println("[RAG] 关键词: " + keywords);
        System.out.println("[RAG] 返回上下文 " + context.length() + " 字");

        return context.toString();
    }

    // ======================== Keyword extraction ========================

    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();

        // Section numbers: 8.1, 3.2.1, 第5章
        java.util.regex.Matcher sm = SECTION_PATTERN.matcher(query);
        while (sm.find()) {
            String term = sm.group();
            keywords.add(term);
            // Add variations
            if (term.matches("\\d+\\.\\d+.*") && !term.startsWith("第")) {
                keywords.add("第" + term + "章");
                keywords.add("第" + term + "节");
            }
        }

        // Quoted phrases
        java.util.regex.Matcher qm = QUOTED_PATTERN.matcher(query);
        while (qm.find()) {
            String phrase = qm.group(1);
            if (phrase.length() >= 2) keywords.add(phrase);
        }

        // Words >= 3 chars that look like technical terms (Chinese chars only)
        java.util.regex.Matcher tm = Pattern.compile("[\\u4e00-\\u9fff]{3,8}").matcher(query);
        while (tm.find()) {
            String term = tm.group();
            // Skip common stop words
            if (!isStopWord(term)) keywords.add(term);
        }

        return keywords;
    }

    private boolean isStopWord(String word) {
        Set<String> stops = Set.of(
                "请问一下", "告诉我", "能不能", "可不可以", "有没有", "是什么",
                "怎么样", "为什么", "在哪里", "怎么办", "什么意思", "帮我",
                "我想知道", "我想问", "我需要", "请帮我", "你可不可以",
                "关于这个", "这个问题", "相关的内容", "相关内容"
        );
        return stops.contains(word) || word.length() < 3;
    }

    // Helper class
    private static class ScoredChunk {
        final TextSegment segment;
        final double finalScore;
        final double semanticScore;
        final boolean keywordBoosted;

        ScoredChunk(TextSegment s, double fs, double ss, boolean kb) {
            segment = s; finalScore = fs; semanticScore = ss; keywordBoosted = kb;
        }
    }
}
