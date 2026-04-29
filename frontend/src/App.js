// import React, { useState, useRef } from 'react';
// import { Container, TextField, IconButton, Paper, Typography, Box, CircularProgress } from '@mui/material';
// import { Send, AttachFile } from '@mui/icons-material';
//
// function App() {
//   const [messages, setMessages] = useState([]);
//   const [input, setInput] = useState('');
//   const [loading, setLoading] = useState(false);
//   const fileInputRef = useRef(null);
//
//   const handleSendText = async () => {
//     if (!input.trim()) return;
//
//     const newMsgs = [...messages, { role: 'user', content: input }];
//     setMessages(newMsgs);
//     setInput('');
//     setLoading(true);
//
//     // 预先推入一个空的 assistant 消息，用来承接流式字元
//     setMessages(prev => [...prev, { role: 'assistant', content: '' }]);
//
//     try {
//       const response = await fetch('http://localhost:8080/api/chat', {
//         method: 'POST',
//         headers: { 'Content-Type': 'application/json' },
//         body: JSON.stringify({ messages: newMsgs })
//       });
//
//       if (!response.body) throw new Error('ReadableStream not yet supported in this browser.');
//
//       const reader = response.body.getReader();
//       const decoder = new TextDecoder('utf-8');
//       let done = false;
//
//       while (!done) {
//         const { value, done: readerDone } = await reader.read();
//         done = readerDone;
//         if (value) {
//           // 解码二进制流
//           const chunk = decoder.decode(value, { stream: true });
//
//           // 解析 SSE 格式 (Spring 会发送类似 "data: 你好\n\n" 的格式)
//           const lines = chunk.split('\n');
//           let textToAppend = '';
//
//           for (const line of lines) {
//             if (line.startsWith('data:')) {
//               // 截取 "data:" 后面的实际内容
//               const dataContent = line.substring(5);
//               textToAppend += dataContent;
//             }
//           }
//
//           if (textToAppend) {
//             // 增量更新最后一条消息
//             setMessages(prev => {
//               const updated = [...prev];
//               updated[updated.length - 1].content += textToAppend;
//               return updated;
//             });
//           }
//         }
//       }
//     } catch (e) {
//       console.error('Stream error:', e);
//       setMessages(prev => {
//         const updated = [...prev];
//         updated[updated.length - 1].content += "\n[流式连接断开，请检查后端服务]";
//         return updated;
//       });
//     } finally {
//       setLoading(false);
//     }
//   };
//
//   // const handleFileUpload = async (e) => {
//   //   const file = e.target.files[0];
//   //   if (!file) return;
//   //   let type = file.type.includes('image') ? 'image' : (file.type.includes('audio') ? 'audio' : 'video');
//   //   setMessages([...messages, { role: 'user', content: `[文件: ${file.name}]` }]);
//   //   setLoading(true);
//   //   setMessages(prev => [...prev, { role: 'assistant', content: '正在处理多模态数据...' }]);
//   //
//   //   const formData = new FormData();
//   //   formData.append('file', file);
//   //   formData.append('type', type);
//   //   formData.append('prompt', input || "请分析");
//   //
//   //   try {
//   //     const response = await fetch('http://localhost:8080/api/chat/media', { method: 'POST', body: formData });
//   //     const result = await response.text();
//   //     setMessages(prev => {
//   //       const updated = [...prev];
//   //       updated[updated.length - 1].content = result;
//   //       return updated;
//   //     });
//   //   } catch (e) {
//   //     console.error(e);
//   //     setMessages(prev => {
//   //       const updated = [...prev];
//   //       updated[updated.length - 1].content = "文件处理失败，请检查后端服务是否正常运行。";
//   //       return updated;
//   //     });
//   //   }
//   //   finally { setLoading(false); }
//   // };
//   const handleFileUpload = async (e) => {
//     const file = e.target.files[0];
//     if (!file) return;
//     let type = file.type.includes('image') ? 'image' : (file.type.includes('audio') ? 'audio' : 'video');
//     setMessages([...messages, { role: 'user', content: `[文件: ${file.name}]` }]);
//     setLoading(true);
//
//     // 预留一个空的 assistant 消息位置，用来逐字拼接流式文本
//     setMessages(prev => [...prev, { role: 'assistant', content: '' }]);
//
//     const formData = new FormData();
//     formData.append('file', file);
//     formData.append('type', type);
//     formData.append('prompt', input || "请分析");
//     setInput(''); // 发送后清空输入框
//
//     try {
//       const response = await fetch('http://localhost:8080/api/chat/media', {
//         method: 'POST',
//         body: formData
//       });
//
//       if (!response.body) throw new Error('当前浏览器不支持流式读取');
//
//       // 开启流式读取器
//       const reader = response.body.getReader();
//       const decoder = new TextDecoder('utf-8');
//       let done = false;
//
//       while (!done) {
//         const { value, done: readerDone } = await reader.read();
//         done = readerDone;
//         if (value) {
//           const chunk = decoder.decode(value, { stream: true });
//           const lines = chunk.split('\n');
//
//           let textToAppend = '';
//           let eventData = []; // 用来暂存同一个事件中的多行数据
//
//           for (const line of lines) {
//             if (line.startsWith('data:')) {
//               // 把 "data:" 后面的内容推进数组
//               eventData.push(line.substring(5));
//             } else if (line === '') {
//               // 遇到空行，代表一个流事件结束，把刚刚收集的行用换行符拼回去！
//               if (eventData.length > 0) {
//                 textToAppend += eventData.join('\n');
//                 eventData = [];
//               }
//             }
//           }
//
//           // 防止 chunk 被截断导致最后一段没有空行
//           if (eventData.length > 0) {
//             textToAppend += eventData.join('\n');
//           }
//
//           if (textToAppend) {
//             // 实时渲染到最后一条消息中
//             setMessages(prev => {
//               const updated = [...prev];
//               updated[updated.length - 1].content += textToAppend;
//               return updated;
//             });
//           }
//         }
//       }
//     } catch (e) {
//       console.error('文件处理流异常:', e);
//       setMessages(prev => {
//         const updated = [...prev];
//         updated[updated.length - 1].content += "\n\n[文件处理失败，请检查后端服务是否正常]";
//         return updated;
//       });
//     } finally {
//       setLoading(false);
//       // 清空 input file 的值，允许重复上传同一个文件
//       if (fileInputRef.current) {
//         fileInputRef.current.value = null;
//       }
//     }
//   };
//
//
//   return (
//     <Container maxWidth="md" sx={{ height: '100vh', display: 'flex', flexDirection: 'column', py: 2 }}>
//       <Typography variant="h5" sx={{ mb: 2, fontWeight: 'bold', color: '#1976d2' }}>本地多模态 AI 助手</Typography>
//       <Paper sx={{ flex: 1, overflowY: 'auto', p: 2, mb: 2, bgcolor: '#f9f9f9' }}>
//         {messages.map((m, i) => (
//           <Box key={i} sx={{ textAlign: m.role === 'user' ? 'right' : 'left', mb: 2 }}>
//             <Box sx={{ display: 'inline-block', p: 2, borderRadius: 2, bgcolor: m.role === 'user' ? '#1976d2' : '#fff', color: m.role === 'user' ? '#fff' : '#333', boxShadow: 1, maxWidth: '85%', textAlign: 'left' }}>
//               <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>{m.content}</Typography>
//             </Box>
//           </Box>
//         ))}
//       </Paper>
//       <Box sx={{ display: 'flex', gap: 1 }}>
//         <input type="file" hidden ref={fileInputRef} onChange={handleFileUpload} />
//         <IconButton onClick={() => fileInputRef.current.click()}><AttachFile /></IconButton>
//         <TextField fullWidth value={input} onChange={e => setInput(e.target.value)} onKeyPress={e => e.key === 'Enter' && handleSendText()} placeholder="问点什么..." />
//         <IconButton onClick={handleSendText} disabled={loading} color="primary"><Send /></IconButton>
//       </Box>
//     </Container>
//   );
// }
//
// export default App;



import React, { useState, useRef, useEffect } from 'react';
import { Container, TextField, IconButton, Paper, Typography, Box, Drawer, List, ListItem, ListItemIcon, ListItemText, Divider, Button } from '@mui/material';
import { Send, AttachFile, ChatBubbleOutline, FolderShared, UploadFile, Logout } from '@mui/icons-material';
import ReactMarkdown from 'react-markdown';
import Login from './Login';

function App() {
  const [isAuth, setIsAuth] = useState(!!localStorage.getItem('token'));
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [knowledgeFiles, setKnowledgeFiles] = useState([]);

  const fileInputRef = useRef(null);
  const kbInputRef = useRef(null);
  const chatEndRef = useRef(null);

  useEffect(() => {
    if (isAuth) {
      fetchHistory();
      fetchKnowledgeFiles();
    }
  }, [isAuth]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const getAuthHeaders = (isFormData = false) => {
    const headers = { 'Authorization': 'Bearer ' + localStorage.getItem('token') };
    if (!isFormData) headers['Content-Type'] = 'application/json';
    return headers;
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('userId');
    setMessages([]);
    setKnowledgeFiles([]);
    setIsAuth(false);
  };

  const fetchHistory = async () => {
    try {
      const res = await fetch('http://localhost:8080/api/history', { headers: getAuthHeaders() });
      if (res.status === 401 || res.status === 403) return handleLogout();
      const data = await res.json();
      setMessages(data);
    } catch (e) { console.error("加载历史记录失败", e); }
  };

  const fetchKnowledgeFiles = async () => {
    try {
      const res = await fetch('http://localhost:8080/api/knowledge/files', { headers: getAuthHeaders() });
      const data = await res.json();
      setKnowledgeFiles(data);
    } catch (e) { console.error("加载知识库失败", e); }
  };

  const handleKBUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    try {
      await fetch('http://localhost:8080/api/knowledge/upload', {
        method: 'POST',
        headers: getAuthHeaders(true),
        body: formData
      });
      fetchKnowledgeFiles();
    } catch (e) { console.error("知识库上传失败"); }
  };

  // 📝 终极完美版流式解析：精准还原大模型的原生换行
  const parseSSEStream = async (response, isMedia = false) => {
    if (!response.body) throw new Error('流式读取不支持');
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let done = false;

    while (!done) {
      const { value, done: readerDone } = await reader.read();
      done = readerDone;
      if (value) {
        const chunk = decoder.decode(value, { stream: true });
        // SSE 协议中，每个事件以 \n\n 结束，我们按此分割
        const events = chunk.split('\n\n');
        let textToAppend = '';

        for (const event of events) {
          if (!event.trim()) continue;

          // 提取出所有以 data: 开头的行，并精准还原它们内部的换行符
          const lines = event.split('\n');
          const eventText = lines
              .filter(line => line.startsWith('data:'))
              .map(line => {
                let content = line.substring(5);
                if (content.startsWith(' ')) content = content.substring(1); // 去除 Spring 加的空格前缀
                return content;
              })
              .join('\n'); // 核心修复：把被切割的换行符原封不动地拼回去！

          textToAppend += eventText;
        }

        if (textToAppend) {
          setMessages(prev => {
            const updated = [...prev];
            updated[updated.length - 1].content += textToAppend;
            return updated;
          });
        }
      }
    }
  };

  const handleSendText = async () => {
    if (!input.trim()) return;

    const newMsgs = [...messages, { role: 'user', content: input }];
    setMessages(newMsgs);
    setInput('');
    setLoading(true);

    // 预推一个空的 assistant 气泡
    setMessages(prev => [...prev, { role: 'assistant', content: '' }]);

    try {
      const response = await fetch('http://localhost:8080/api/chat', {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ messages: newMsgs })
      });

      if (response.status === 401 || response.status === 403) return handleLogout();

      // 调用封装好的完美流式解析器
      await parseSSEStream(response);

    } catch (e) {
      console.error('Error:', e);
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1].content += "\n[网络连接异常或后端服务未启动]";
        return updated;
      });
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    let type = file.type.includes('image') ? 'image' : (file.type.includes('audio') ? 'audio' : 'video');

    setMessages([...messages, { role: 'user', content: `[上传了文件: ${file.name}]\n${input || "请分析这个文件"}` }]);
    setLoading(true);
    setMessages(prev => [...prev, { role: 'assistant', content: '' }]);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);
    formData.append('prompt', input || "请分析这个文件");
    setInput('');

    try {
      const response = await fetch('http://localhost:8080/api/chat/media', {
        method: 'POST',
        headers: getAuthHeaders(true),
        body: formData
      });

      if (response.status === 401 || response.status === 403) return handleLogout();

      // 调用封装好的完美流式解析器
      await parseSSEStream(response, true);

    } catch (e) {
      console.error('文件处理流异常:', e);
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1].content += "\n[文件处理失败，请检查后端服务是否正常]";
        return updated;
      });
    } finally {
      setLoading(false);
      if (fileInputRef.current) fileInputRef.current.value = null;
    }
  };

  if (!isAuth) {
    return <Login onLoginSuccess={() => setIsAuth(true)} />;
  }

  return (
      <Box sx={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden', bgcolor: '#f4f6f8' }}>

        {/* 左侧栏：严格定宽 260px，不参与 Flex 缩放 */}
        <Drawer variant="permanent" sx={{ width: 260, flexShrink: 0, '& .MuiDrawer-paper': { width: 260, boxSizing: 'border-box', bgcolor: '#1c2536', color: '#fff', display: 'flex', flexDirection: 'column' } }}>
          <Box sx={{ p: 2 }}>
            <Typography variant="h6" fontWeight="bold">AI 商业智能台</Typography>
            <Typography variant="caption" color="gray">当前用户: {localStorage.getItem('username')}</Typography>
          </Box>
          <Divider sx={{ bgcolor: 'rgba(255,255,255,0.1)' }} />
          <List sx={{ flexGrow: 1 }}>
            <ListItem button>
              <ListItemIcon><ChatBubbleOutline sx={{ color: '#fff' }}/></ListItemIcon>
              <ListItemText primary="默认会话空间" />
            </ListItem>
          </List>
          <Divider sx={{ bgcolor: 'rgba(255,255,255,0.1)' }} />
          <Box sx={{ p: 2 }}>
            <Button
                fullWidth variant="outlined" color="error" startIcon={<Logout />} onClick={handleLogout}
                sx={{ borderColor: 'rgba(244,67,54,0.5)', color: '#f44336', '&:hover': { borderColor: '#f44336', bgcolor: 'rgba(244,67,54,0.1)' } }}
            >
              退出登录
            </Button>
          </Box>
        </Drawer>

        {/* 👇 修复 2：中间交互区：使用 flex: 1 和黄金属性 minWidth: 0，确保它只能在两个边栏的夹缝中生存，绝对不会挤出去 */}
        <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', height: '100vh', p: 3, boxSizing: 'border-box' }}>

          {/* 聊天记录列表区：占据剩余空间并允许纵向滚动 */}
          <Paper elevation={0} sx={{ flex: 1, overflowY: 'auto', p: 3, mb: 2, borderRadius: 3, bgcolor: '#fff', border: '1px solid #e0e0e0' }}>
            {messages.map((m, i) => (
                <Box key={i} sx={{ display: 'flex', justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start', mb: 3 }}>
                  <Box sx={{
                    p: 2, borderRadius: 2,
                    bgcolor: m.role === 'user' ? '#1976d2' : '#f8f9fa',
                    color: m.role === 'user' ? '#fff' : '#111827',
                    maxWidth: '80%', // 限制单条消息气泡的最大宽度
                    border: m.role === 'assistant' ? '1px solid #e5e7eb' : 'none',
                    // 👇 修复 3：强化 Markdown 样式，限制内部图片和代码块的宽度，避免撑破气泡
                    '& img': { maxWidth: '100%', height: 'auto' },
                    '& p': { margin: 0, marginBottom: '0.8em', lineHeight: 1.6, wordBreak: 'break-word' },
                    '& ul, & ol': { marginTop: '0.5em', marginBottom: '0.5em', paddingLeft: '1.5em' },
                    '& li': { marginBottom: '0.3em' },
                    '& pre': { background: '#f5f5f5', padding: '10px', borderRadius: '4px', overflowX: 'auto', maxWidth: '100%' },
                    '& code': { background: '#f5f5f5', padding: '2px 4px', borderRadius: '4px', color: '#d32f2f', wordBreak: 'break-word' }
                  }}>
                    <ReactMarkdown>{m.content}</ReactMarkdown>
                  </Box>
                </Box>
            ))}
            <div ref={chatEndRef} />
          </Paper>

          {/* 底部输入框区：固定在底部，不参与挤压 */}
          <Box sx={{ flexShrink: 0, display: 'flex', gap: 1, alignItems: 'center', bgcolor: '#fff', p: 1, borderRadius: 3, border: '1px solid #e0e0e0' }}>
            <input type="file" hidden ref={fileInputRef} onChange={handleFileUpload} />
            <IconButton onClick={() => fileInputRef.current.click()}><AttachFile /></IconButton>
            <TextField variant="standard" fullWidth value={input} onChange={e => setInput(e.target.value)} onKeyPress={e => e.key === 'Enter' && handleSendText()} placeholder="输入指令，或上传媒体文件..." InputProps={{ disableUnderline: true, sx: { px: 1 } }} />
            <IconButton onClick={handleSendText} disabled={loading} color="primary" sx={{ bgcolor: '#e3f2fd' }}><Send /></IconButton>
          </Box>
        </Box>

        {/* 右侧栏：严格定宽 280px，不参与 Flex 缩放 */}
        <Drawer variant="permanent" anchor="right" sx={{ width: 280, flexShrink: 0, '& .MuiDrawer-paper': { width: 280, boxSizing: 'border-box', bgcolor: '#fff', borderLeft: '1px solid #e0e0e0' } }}>
          <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="subtitle1" fontWeight="bold">私有知识库 (RAG)</Typography>
            <input type="file" hidden ref={kbInputRef} onChange={handleKBUpload} />
            <IconButton size="small" onClick={() => kbInputRef.current.click()} color="primary"><UploadFile /></IconButton>
          </Box>
          <Divider />
          <List sx={{ px: 1, overflowY: 'auto', flex: 1 }}>
            {knowledgeFiles.length === 0 ? (
                <Typography variant="caption" color="textSecondary" sx={{ p: 2, display: 'block', textAlign: 'center' }}>暂无上传文档</Typography>
            ) : (
                knowledgeFiles.map((file, i) => (
                    <ListItem key={i} sx={{ bgcolor: '#f4f6f8', borderRadius: 1, mb: 1 }}>
                      <ListItemIcon sx={{ minWidth: 36 }}><FolderShared fontSize="small" color="action" /></ListItemIcon>
                      <ListItemText primary={file} primaryTypographyProps={{ noWrap: true, variant: 'body2' }} />
                    </ListItem>
                ))
            )}
          </List>
        </Drawer>
      </Box>
  );
}
export default App;