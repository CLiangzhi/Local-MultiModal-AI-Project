import React, { useRef, useEffect, useState } from 'react';
import { Box, Paper, TextField, IconButton, Typography, Chip, Dialog, DialogTitle, DialogContent, DialogActions, Button } from '@mui/material';
import { Send, AttachFile, SmartToy, LightbulbOutlined, Image } from '@mui/icons-material';
import MessageBubble from './MessageBubble';

export default function ChatArea({
  messages, loading, agentMode, imageGenMode, onImageGenModeChange,
  conversationId,
  onSend, onFileUpload, onImageGenerate, onEditMessage, onBranchSwitch,
}) {
  const fileInputRef = useRef(null);
  const chatEndRef = useRef(null);
  const [input, setInput] = useState('');
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editingMsg, setEditingMsg] = useState(null);
  const [editContent, setEditContent] = useState('');

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = () => {
    if (!input.trim()) return;
    if (imageGenMode) {
      onImageGenerate(input);
    } else {
      onSend(input);
    }
    setInput('');
  };

  const handleEditClick = (msg) => {
    setEditingMsg(msg);
    setEditContent(msg.content);
    setEditDialogOpen(true);
  };

  const handleEditConfirm = () => {
    if (editContent.trim() && editingMsg) {
      onEditMessage(editingMsg, editContent.trim());
      setEditDialogOpen(false);
      setEditingMsg(null);
      setEditContent('');
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      onFileUpload(file, input);
      setInput('');
      if (fileInputRef.current) fileInputRef.current.value = null;
    }
  };

  const hasMessages = messages.length > 0;

  return (
    <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', height: '100vh', p: 3, boxSizing: 'border-box' }}>
      {/* Messages area */}
      <Paper elevation={0} sx={{
        flex: 1, overflowY: 'auto', p: 3, mb: 2, borderRadius: 3,
        bgcolor: '#fff', border: '1px solid #e0e0e0',
        display: 'flex', flexDirection: 'column',
      }}>
        {!hasMessages ? (
          /* Welcome screen */
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: '#aaa' }}>
            <LightbulbOutlined sx={{ fontSize: 64, mb: 2, color: '#ccc' }} />
            <Typography variant="h6" color="#999" gutterBottom>
              {imageGenMode ? 'AI 图片生成模式' : (agentMode ? 'AI Agent 模式已启动' : '有什么可以帮你的？')}
            </Typography>
            <Typography variant="body2" color="#bbb" sx={{ mb: 3, textAlign: 'center', maxWidth: 400 }}>
              {imageGenMode
                ? '使用 Pollinations.ai 生成图片。描述你想要的画面，AI 将为你创作。'
                : (agentMode
                  ? 'Agent 可以读取文件、执行命令、搜索知识库。试试让它帮你完成任务。'
                  : '发送消息开始对话，或上传图片/音频/视频进行多模态分析。')}
            </Typography>
            {/* Quick start chips */}
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, justifyContent: 'center', maxWidth: 500 }}>
              {imageGenMode ? (
                <>
                  {['一只橙色的猫坐在窗台上看日落', '未来城市的天际线，赛博朋克风格', '宁静的日式庭院，樱花飘落'].map((q, i) => (
                    <Chip key={i} label={q} onClick={() => onImageGenerate(q)} sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#e8f5e9' } }} />
                  ))}
                </>
              ) : agentMode ? (
                <>
                  {['帮我查找桌面上有哪些文件', '搜索知识库中关于RAG的资料', '查看local_data目录下有什么'].map((q, i) => (
                    <Chip key={i} label={q} onClick={() => onSend(q)} sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#fff3e0' } }} />
                  ))}
                </>
              ) : (
                <>
                  {['帮我分析这张图片', '总结一下今天的对话', '介绍一下RAG技术'].map((q, i) => (
                    <Chip key={i} label={q} onClick={() => onSend(q)} sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#e3f2fd' } }} />
                  ))}
                </>
              )}
            </Box>
          </Box>
        ) : (
          messages.map((msg, i) => (
            <MessageBubble
              key={i}
              msg={msg}
              onEdit={agentMode ? null : handleEditClick}
              onBranchSwitch={onBranchSwitch}
              branchOptions={msg.branchOptions}
            />
          ))
        )}
        <div ref={chatEndRef} />
      </Paper>

      {/* Input bar */}
      <Box sx={{
        flexShrink: 0, display: 'flex', gap: 1, alignItems: 'center',
        bgcolor: '#fff', p: 1, borderRadius: 3, border: '1px solid #e0e0e0',
      }}>
        <input type="file" hidden ref={fileInputRef} onChange={handleFileChange}
          accept="image/*,audio/*,video/*" />
        <IconButton onClick={() => fileInputRef.current.click()} disabled={agentMode || imageGenMode}>
          <AttachFile />
        </IconButton>

        <TextField
          variant="standard" fullWidth
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyPress={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
          placeholder={imageGenMode ? '描述你想要生成的图片...' : (agentMode ? '输入Agent指令，例如：帮我查找文件...' : '输入指令，或上传媒体文件...')}
          InputProps={{ disableUnderline: true, sx: { px: 1 } }}
          multiline
          maxRows={4}
        />

        {imageGenMode && (
          <Chip
            icon={<Image />} label="图片生成" size="small"
            sx={{ bgcolor: '#e8f5e9', color: '#2e7d32', fontWeight: 'bold' }}
          />
        )}

        {agentMode && (
          <Chip
            icon={<SmartToy />} label="Agent" size="small"
            sx={{ bgcolor: '#fff3e0', color: '#e65100', fontWeight: 'bold' }}
          />
        )}

        <IconButton
          onClick={() => onImageGenModeChange(!imageGenMode)}
          disabled={agentMode}
          color={imageGenMode ? 'success' : 'default'}
          title="图片生成模式"
        >
          <Image />
        </IconButton>

        <IconButton onClick={handleSend} disabled={loading} color="primary"
          sx={{ bgcolor: imageGenMode ? '#e8f5e9' : (agentMode ? '#fff3e0' : '#e3f2fd') }}>
          <Send />
        </IconButton>
      </Box>

      {/* Edit / Branch Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>编辑消息并创建分支</DialogTitle>
        <DialogContent>
          <Typography variant="caption" color="textSecondary" sx={{ mb: 1, display: 'block' }}>
            修改后将从此消息创建新的对话分支
          </Typography>
          <TextField
            fullWidth multiline minRows={3} maxRows={8} autoFocus
            value={editContent}
            onChange={e => setEditContent(e.target.value)}
            onKeyPress={e => {
              if (e.key === 'Enter' && e.ctrlKey) handleEditConfirm();
            }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>取消</Button>
          <Button variant="contained" onClick={handleEditConfirm}>创建分支</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
