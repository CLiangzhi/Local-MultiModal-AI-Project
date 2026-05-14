import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Box } from '@mui/material';
import Login from './Login';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import KnowledgePanel from './components/KnowledgePanel';

const API = 'http://localhost:8080';

function App() {
  // Auth
  const [isAuth, setIsAuth] = useState(!!localStorage.getItem('token'));

  // Conversations
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);

  // Messages
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);

  // Agent mode
  const [agentMode, setAgentMode] = useState(false);

  // Knowledge base
  const [knowledgeFiles, setKnowledgeFiles] = useState([]);
  const [uploadingKB, setUploadingKB] = useState(false);

  const getAuthHeaders = (isFormData = false) => {
    const headers = { 'Authorization': 'Bearer ' + localStorage.getItem('token') };
    if (!isFormData) headers['Content-Type'] = 'application/json';
    return headers;
  };

  const handleLogout = () => {
    ['token', 'username', 'userId'].forEach(k => localStorage.removeItem(k));
    setMessages([]);
    setConversations([]);
    setKnowledgeFiles([]);
    setActiveConversationId(null);
    setAgentMode(false);
    setIsAuth(false);
  };

  // ================== Conversation APIs ==================

  const fetchConversations = async () => {
    try {
      const res = await fetch(API + '/api/conversations', { headers: getAuthHeaders() });
      if (res.status === 401 || res.status === 403) return handleLogout();
      const data = await res.json();
      setConversations(data);
      // Auto-select first conversation if none active
      if (data.length > 0 && !activeConversationId) {
        setActiveConversationId(data[0].id);
      }
    } catch (e) { console.error('加载会话失败', e); }
  };

  const createConversation = async (title) => {
    try {
      const res = await fetch(API + '/api/conversations', {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ title })
      });
      const conv = await res.json();
      setConversations(prev => [conv, ...prev]);
      setActiveConversationId(conv.id);
      setMessages([]);
    } catch (e) { console.error('创建会话失败', e); }
  };

  const deleteConversation = async (id) => {
    try {
      await fetch(API + '/api/conversations/' + id, {
        method: 'DELETE',
        headers: getAuthHeaders()
      });
      setConversations(prev => prev.filter(c => c.id !== id));
      if (activeConversationId === id) {
        setActiveConversationId(null);
        setMessages([]);
      }
    } catch (e) { console.error('删除会话失败', e); }
  };

  const selectConversation = async (id) => {
    setActiveConversationId(id);
    try {
      const res = await fetch(API + '/api/conversations/' + id + '/messages', {
        headers: getAuthHeaders()
      });
      if (res.status === 401 || res.status === 403) return handleLogout();
      const data = await res.json();
      setMessages(data);
    } catch (e) { console.error('加载消息失败', e); }
  };

  const switchBranch = async (messageId) => {
    if (!activeConversationId) return;
    try {
      const res = await fetch(
        API + '/api/conversations/' + activeConversationId + '/switch-branch/' + messageId,
        { method: 'POST', headers: getAuthHeaders() }
      );
      if (res.ok) {
        // Reload messages to show the new active branch
        selectConversation(activeConversationId);
      }
    } catch (e) { console.error('切换分支失败', e); }
  };

  // ================== Knowledge Base APIs ==================

  const fetchKnowledgeFiles = async () => {
    try {
      const res = await fetch(API + '/api/knowledge/files', { headers: getAuthHeaders() });
      if (res.status === 401 || res.status === 403) return handleLogout();
      const data = await res.json();
      setKnowledgeFiles(data);
    } catch (e) { console.error('加载知识库失败', e); }
  };

  const handleKBUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setUploadingKB(true);
    const formData = new FormData();
    formData.append('file', file);
    try {
      await fetch(API + '/api/knowledge/upload', {
        method: 'POST',
        headers: getAuthHeaders(true),
        body: formData
      });
      fetchKnowledgeFiles();
    } catch (e) { console.error('知识库上传失败', e); }
    setUploadingKB(false);
  };

  // ================== SSE Stream Parser ==================

  const parseSSEStream = async (response, isAgent = false) => {
    if (!response.body) throw new Error('流式读取不支持');
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let done = false;
    let buffer = '';

    while (!done) {
      const { value, done: readerDone } = await reader.read();
      done = readerDone;
      if (value) {
        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split('\n\n');
        buffer = events.pop() || ''; // Keep incomplete event in buffer

        for (const event of events) {
          if (!event.trim()) continue;

          const lines = event.split('\n');
          const eventText = lines
            .filter(line => line.startsWith('data:'))
            .map(line => {
              let content = line.substring(5);
              if (content.startsWith(' ')) content = content.substring(1);
              return content;
            })
            .join('\n');

          if (!eventText) continue;

          // Handle agent events
          if (isAgent) {
            if (eventText.startsWith('[AGENT_THINKING]')) {
              // Show thinking indicator as a tool message
              setMessages(prev => [...prev, {
                role: 'tool',
                content: eventText.replace('[AGENT_THINKING] ', ''),
                thinking: true
              }]);
              continue;
            }
            if (eventText.startsWith('[TOOL_CALL]')) {
              try {
                const tc = JSON.parse(eventText.substring('[TOOL_CALL] '.length));
                setMessages(prev => [...prev, {
                  role: 'tool',
                  content: '调用工具: ' + tc.name,
                  toolCallData: tc,
                  isToolCall: true
                }]);
              } catch (e) { /* malformed JSON, skip */ }
              continue;
            }
            if (eventText.startsWith('[TOOL_RESULT]')) {
              try {
                const tr = JSON.parse(eventText.substring('[TOOL_RESULT] '.length));
                setMessages(prev => [...prev, {
                  role: 'tool',
                  content: tr.result || '',
                  toolName: tr.name,
                  isToolResult: true
                }]);
              } catch (e) { /* malformed JSON, skip */ }
              continue;
            }
            if (eventText.startsWith('[AGENT_ERROR]')) {
              setMessages(prev => [...prev, {
                role: 'tool',
                content: '❌ ' + eventText.replace('[AGENT_ERROR] ', ''),
                isError: true
              }]);
              continue;
            }
          }

          // Regular content - append to last message or create new one
          setMessages(prev => {
            const updated = [...prev];
            const last = updated[updated.length - 1];

            // If last message is a tool/system message, or doesn't exist, create a new assistant message
            if (!last || last.role === 'tool' || last.role === 'user') {
              updated.push({ role: 'assistant', content: eventText });
            } else {
              // Append to existing assistant message
              updated[updated.length - 1] = {
                ...last,
                content: last.content + eventText
              };
            }
            return updated;
          });
        }
      }
    }
  };

  // ================== Send Messages ==================

  const handleSend = async (text) => {
    if (!text.trim() || loading) return;
    const content = text.trim();

    // Add user message
    const userMsg = { role: 'user', content };
    setMessages(prev => [...prev, userMsg]);
    setLoading(true);

    // Prepend with empty assistant placeholder
    setMessages(prev => [...prev, { role: 'assistant', content: '' }]);

    const endpoint = agentMode ? '/api/chat/agent' : '/api/chat';
    const historyMsgs = messages.map(m => ({
      role: m.role === 'tool' ? 'assistant' : m.role,
      content: m.content
    }));
    historyMsgs.push({ role: 'user', content });

    try {
      const response = await fetch(API + endpoint, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({
          messages: historyMsgs,
          conversationId: activeConversationId,
          parentMessageId: null
        })
      });

      if (response.status === 401 || response.status === 403) return handleLogout();
      await parseSSEStream(response, agentMode);

      // Refresh conversations to get the new/updated one
      fetchConversations();
      // If no active conversation, select the first one after refresh
      if (!activeConversationId) {
        setTimeout(() => {
          setConversations(prev => {
            if (prev.length > 0) setActiveConversationId(prev[0].id);
            return prev;
          });
        }, 300);
      }
    } catch (e) {
      console.error('发送失败:', e);
      setMessages(prev => {
        const updated = [...prev];
        if (updated.length > 0) {
          updated[updated.length - 1] = {
            ...updated[updated.length - 1],
            content: updated[updated.length - 1].content + '\n[网络连接异常或后端服务未启动]'
          };
        }
        return updated;
      });
    } finally {
      setLoading(false);
    }
  };

  const handleEditMessage = async (msg, newContent) => {
    if (!activeConversationId || loading) return;

    // Add the new user message (the "edit")
    setMessages(prev => [...prev, { role: 'user', content: newContent }]);
    setLoading(true);
    setMessages(prev => [...prev, { role: 'assistant', content: '' }]);

    // Build history up to the parent message
    const msgIndex = messages.findIndex(m => m.id === msg.id);
    const historyMsgs = messages.slice(0, msgIndex + 1).map(m => ({
      role: m.role === 'tool' ? 'assistant' : m.role,
      content: m.content
    }));
    historyMsgs.push({ role: 'user', content: newContent });

    const endpoint = agentMode ? '/api/chat/agent' : '/api/chat';

    try {
      const response = await fetch(API + endpoint, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({
          messages: historyMsgs,
          conversationId: activeConversationId,
          parentMessageId: msg.id
        })
      });

      if (response.status === 401 || response.status === 403) return handleLogout();
      await parseSSEStream(response, agentMode);
      // Reload messages to get branch structure
      selectConversation(activeConversationId);
    } catch (e) {
      console.error('分支创建失败:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (file, promptText) => {
    if (loading) return;
    let type = file.type.includes('image') ? 'image'
      : (file.type.includes('audio') ? 'audio' : 'video');

    setMessages(prev => [...prev, {
      role: 'user',
      content: '[上传了文件: ' + file.name + ']\n' + (promptText || '请分析这个文件')
    }]);
    setLoading(true);
    setMessages(prev => [...prev, { role: 'assistant', content: '' }]);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);
    formData.append('prompt', promptText || '请分析这个文件');
    if (activeConversationId) {
      formData.append('conversationId', activeConversationId);
    }

    try {
      const response = await fetch(API + '/api/chat/media', {
        method: 'POST',
        headers: getAuthHeaders(true),
        body: formData
      });

      if (response.status === 401 || response.status === 403) return handleLogout();
      await parseSSEStream(response, false);
      fetchConversations();
    } catch (e) {
      console.error('文件处理失败:', e);
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          ...updated[updated.length - 1],
          content: updated[updated.length - 1].content + '\n[文件处理失败，请检查后端服务]'
        };
        return updated;
      });
    } finally {
      setLoading(false);
    }
  };

  // ================== Effects ==================

  useEffect(() => {
    if (isAuth) {
      fetchConversations();
      fetchKnowledgeFiles();
    }
  }, [isAuth]);

  // ================== Render ==================

  if (!isAuth) {
    return <Login onLoginSuccess={() => setIsAuth(true)} />;
  }

  return (
    <Box sx={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden', bgcolor: '#f4f6f8' }}>
      <Sidebar
        conversations={conversations}
        activeConversationId={activeConversationId}
        onSelectConversation={selectConversation}
        onCreateConversation={createConversation}
        onDeleteConversation={deleteConversation}
        agentMode={agentMode}
        onAgentModeChange={setAgentMode}
        username={localStorage.getItem('username')}
        onLogout={handleLogout}
      />

      <ChatArea
        messages={messages}
        loading={loading}
        agentMode={agentMode}
        conversationId={activeConversationId}
        onSend={handleSend}
        onFileUpload={handleFileUpload}
        onEditMessage={handleEditMessage}
        onBranchSwitch={switchBranch}
      />

      <KnowledgePanel
        files={knowledgeFiles}
        onUpload={handleKBUpload}
        uploading={uploadingKB}
        onFileDeleted={fetchKnowledgeFiles}
      />
    </Box>
  );
}

export default App;
