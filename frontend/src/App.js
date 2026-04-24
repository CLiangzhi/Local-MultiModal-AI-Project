import React, { useState, useRef } from 'react';
import { Container, TextField, IconButton, Paper, Typography, Box, CircularProgress } from '@mui/material';
import { Send, AttachFile } from '@mui/icons-material';

function App() {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const fileInputRef = useRef(null);

  // const handleSendText = async () => {
  //   if (!input.trim()) return;
  //   const newMsgs = [...messages, { role: 'user', content: input }];
  //   setMessages(newMsgs);
  //   setInput('');
  //   setLoading(true);
  //   setMessages(prev => [...prev, { role: 'assistant', content: '' }]);
  //
  //   try {
  //     const response = await fetch('http://localhost:8080/api/chat', {
  //       method: 'POST',
  //       headers: { 'Content-Type': 'application/json' },
  //       body: JSON.stringify({ messages: newMsgs })
  //     });
  //     const reader = response.body.getReader();
  //     const decoder = new TextDecoder();
  //     while (true) {
  //       const { value, done } = await reader.read();
  //       if (done) break;
  //       const chunk = decoder.decode(value);
  //       setMessages(prev => {
  //         const updated = [...prev];
  //         updated[updated.length - 1].content += chunk;
  //         return updated;
  //       });
  //     }
  //   } catch (e) { console.error(e); }
  //   finally { setLoading(false); }
  // };
  const handleSendText = async () => {
    if (!input.trim()) return;
    const newMsgs = [...messages, { role: 'user', content: input }];
    setMessages(newMsgs);
    setInput('');
    setLoading(true);
    setMessages(prev => [...prev, { role: 'assistant', content: '正在思考...' }]);

    try {
      const response = await fetch('http://localhost:8080/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: newMsgs })
      });
      const result = await response.text();
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1].content = result;
        return updated;
      });
    } catch (e) {
      console.error(e);
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1].content = "请求失败，请检查后端服务是否正常运行。";
        return updated;
      });
    }
    finally { setLoading(false); }
  };


  // const handleFileUpload = async (e) => {
  //   const file = e.target.files[0];
  //   if (!file) return;
  //   let type = file.type.includes('image') ? 'image' : (file.type.includes('audio') ? 'audio' : 'video');
  //   setMessages([...messages, { role: 'user', content: `[文件: ${file.name}]` }]);
  //   setLoading(true);
  //   setMessages(prev => [...prev, { role: 'assistant', content: '正在处理多模态数据...' }]);
  //
  //   const formData = new FormData();
  //   formData.append('file', file);
  //   formData.append('type', type);
  //   formData.append('prompt', input || "请分析");
  //
  //   try {
  //     const response = await fetch('http://localhost:8080/api/chat/media', { method: 'POST', body: formData });
  //     const reader = response.body.getReader();
  //     const decoder = new TextDecoder();
  //     while (true) {
  //       const { value, done } = await reader.read();
  //       if (done) break;
  //       setMessages(prev => {
  //         const updated = [...prev];
  //         updated[updated.length - 1].content = decoder.decode(value);
  //         return updated;
  //       });
  //     }
  //   } catch (e) { console.error(e); }
  //   finally { setLoading(false); }
  // };
  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    let type = file.type.includes('image') ? 'image' : (file.type.includes('audio') ? 'audio' : 'video');
    setMessages([...messages, { role: 'user', content: `[文件: ${file.name}]` }]);
    setLoading(true);
    setMessages(prev => [...prev, { role: 'assistant', content: '正在处理多模态数据...' }]);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);
    formData.append('prompt', input || "请分析");

    try {
      const response = await fetch('http://localhost:8080/api/chat/media', { method: 'POST', body: formData });
      const result = await response.text();
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1].content = result;
        return updated;
      });
    } catch (e) {
      console.error(e);
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1].content = "文件处理失败，请检查后端服务是否正常运行。";
        return updated;
      });
    }
    finally { setLoading(false); }
  };


  return (
    <Container maxWidth="md" sx={{ height: '100vh', display: 'flex', flexDirection: 'column', py: 2 }}>
      <Typography variant="h5" sx={{ mb: 2, fontWeight: 'bold', color: '#1976d2' }}>本地多模态 AI 助手</Typography>
      <Paper sx={{ flex: 1, overflowY: 'auto', p: 2, mb: 2, bgcolor: '#f9f9f9' }}>
        {messages.map((m, i) => (
          <Box key={i} sx={{ textAlign: m.role === 'user' ? 'right' : 'left', mb: 2 }}>
            <Box sx={{ display: 'inline-block', p: 2, borderRadius: 2, bgcolor: m.role === 'user' ? '#1976d2' : '#fff', color: m.role === 'user' ? '#fff' : '#333', boxShadow: 1, maxWidth: '85%', textAlign: 'left' }}>
              <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>{m.content}</Typography>
            </Box>
          </Box>
        ))}
      </Paper>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <input type="file" hidden ref={fileInputRef} onChange={handleFileUpload} />
        <IconButton onClick={() => fileInputRef.current.click()}><AttachFile /></IconButton>
        <TextField fullWidth value={input} onChange={e => setInput(e.target.value)} onKeyPress={e => e.key === 'Enter' && handleSendText()} placeholder="问点什么..." />
        <IconButton onClick={handleSendText} disabled={loading} color="primary"><Send /></IconButton>
      </Box>
    </Container>
  );
}

export default App;
