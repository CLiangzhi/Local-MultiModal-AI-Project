package com.example.ai.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.embedding.bge.small.zh.q.BgeSmallZhQuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
@Service
public class RagService {

    // 初始化进程内中文微型向量模型
    private final EmbeddingModel embeddingModel = new BgeSmallZhQuantizedEmbeddingModel();    private final Path rootLocation = Paths.get("local_data/knowledge_base");

    /**
     * 核心步骤 1：文档摄入与向量化 (Ingestion)
     * 当用户上传文件时调用此方法
     */
    public void ingestDocumentForUser(String userId, Path filePath) {
        try {
            // 1. 自动识别并解析 Word 或 PDF
            Document document;
            String fileName = filePath.toString().toLowerCase();
            if (fileName.endsWith(".pdf")) {
                document = loadDocument(filePath, new ApachePdfBoxDocumentParser());
            } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                document = loadDocument(filePath, new ApachePoiDocumentParser());
            } else {
                throw new RuntimeException("目前仅支持 PDF 和 Word 文档的向量化");
            }

            // 2. 文本切片 (把长文切成最大 500 字的段落，保留 50 字重叠防截断)
            dev.langchain4j.data.document.DocumentSplitter splitter =
                    dev.langchain4j.data.document.splitter.DocumentSplitters.recursive(500, 50);
            List<TextSegment> segments = splitter.split(document);

            // 3. 加载该用户专属的本地向量库 (如果没有就新建)
            Path storePath = rootLocation.resolve(userId).resolve("vector_store.json");
            InMemoryEmbeddingStore<TextSegment> embeddingStore;
            if (Files.exists(storePath)) {
                embeddingStore = InMemoryEmbeddingStore.fromFile(storePath);
            } else {
                embeddingStore = new InMemoryEmbeddingStore<>();
            }

            // 4. 将切片向量化并存入库中
            embeddingStore.addAll(embeddingModel.embedAll(segments).content(), segments);

            // 5. 序列化保存回用户的专属文件夹
            embeddingStore.serializeToFile(storePath);
            System.out.println("[RAG] 成功向量化文档并更新至专属知识库: " + filePath.getFileName());

        } catch (Exception e) {
            System.err.println("[RAG Error] 文档向量化失败: " + e.getMessage());
        }
    }

    /**
     * 核心步骤 2：相似度检索 (Retrieval)
     * 当用户提问时，先来这里查相关资料
     */
    public String retrieveContext(String userId, String userQuery) {
        Path storePath = rootLocation.resolve(userId).resolve("vector_store.json");
        if (!Files.exists(storePath)) {
            return ""; // 用户还没有上传过知识库文件
        }

        // 1. 读取该用户的向量库
        InMemoryEmbeddingStore<TextSegment> embeddingStore = InMemoryEmbeddingStore.fromFile(storePath);

        // 2. 将用户的提问转化为向量
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(userQuery).content();

        // 3. 找出最相关的 3 个段落 (最小相似度 0.6)
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding, 3, 0.6);

        if (relevant.isEmpty()) return "";

        // 4. 拼装检索到的上下文
        return relevant.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}