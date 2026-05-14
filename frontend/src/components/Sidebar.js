import React from 'react';
import { Box, Typography, List, ListItem, ListItemText, ListItemIcon, IconButton,
  Divider, Button, TextField, Switch, FormControlLabel, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { ChatBubbleOutline, Add, Delete, Logout, SmartToy, Close } from '@mui/icons-material';
import { useState } from 'react';

export default function Sidebar({
  conversations, activeConversationId, onSelectConversation,
  onCreateConversation, onDeleteConversation,
  agentMode, onAgentModeChange, username, onLogout
}) {
  const [showNewDialog, setShowNewDialog] = useState(false);
  const [newTitle, setNewTitle] = useState('');

  return (
    <Box sx={{
      width: 260, height: '100vh', display: 'flex', flexDirection: 'column',
      bgcolor: '#1c2536', color: '#fff', flexShrink: 0,
    }}>
      {/* Header */}
      <Box sx={{ p: 2 }}>
        <Typography variant="h6" fontWeight="bold">AI 多模态助手</Typography>
        <Typography variant="caption" color="gray">用户: {username}</Typography>

        {/* Agent toggle */}
        <FormControlLabel
          control={
            <Switch
              checked={agentMode}
              onChange={(e) => onAgentModeChange(e.target.checked)}
              size="small"
              sx={{
                '& .MuiSwitch-switchBase.Mui-checked': { color: '#ff9800' },
                '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { bgcolor: '#ff9800' },
              }}
            />
          }
          label={<Typography variant="caption" sx={{ color: agentMode ? '#ff9800' : '#aaa', display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <SmartToy sx={{ fontSize: 16 }} /> Agent 模式
          </Typography>}
          sx={{ mt: 1, ml: 0 }}
        />
      </Box>

      <Divider sx={{ bgcolor: 'rgba(255,255,255,0.1)' }} />

      {/* New Conversation Button */}
      <Box sx={{ p: 1.5 }}>
        <Button
          fullWidth variant="outlined" startIcon={<Add />}
          onClick={() => setShowNewDialog(true)}
          sx={{
            color: '#90caf9', borderColor: 'rgba(144,202,249,0.4)',
            '&:hover': { borderColor: '#90caf9', bgcolor: 'rgba(144,202,249,0.08)' },
            textTransform: 'none',
          }}
        >
          新建会话
        </Button>
      </Box>

      <Divider sx={{ bgcolor: 'rgba(255,255,255,0.1)' }} />

      {/* Conversation List */}
      <List sx={{ flexGrow: 1, overflowY: 'auto', px: 1 }}>
        {conversations.length === 0 ? (
          <Typography variant="caption" sx={{ p: 2, display: 'block', textAlign: 'center', color: '#888' }}>
            暂无会话
          </Typography>
        ) : (
          conversations.map(conv => (
            <ListItem
              key={conv.id}
              button
              onClick={() => onSelectConversation(conv.id)}
              sx={{
                borderRadius: 1, mb: 0.5,
                bgcolor: activeConversationId === conv.id ? 'rgba(144,202,249,0.15)' : 'transparent',
                '&:hover': { bgcolor: 'rgba(255,255,255,0.08)' },
              }}
              secondaryAction={
                <IconButton
                  size="small"
                  onClick={(e) => { e.stopPropagation(); onDeleteConversation(conv.id); }}
                  sx={{ color: 'rgba(255,255,255,0.4)', '&:hover': { color: '#f44336' } }}
                >
                  <Delete sx={{ fontSize: 16 }} />
                </IconButton>
              }
            >
              <ListItemIcon sx={{ minWidth: 36 }}>
                <ChatBubbleOutline sx={{ color: activeConversationId === conv.id ? '#90caf9' : 'rgba(255,255,255,0.5)', fontSize: 20 }} />
              </ListItemIcon>
              <ListItemText
                primary={conv.title || '未命名会话'}
                primaryTypographyProps={{
                  noWrap: true,
                  variant: 'body2',
                  sx: { color: activeConversationId === conv.id ? '#fff' : 'rgba(255,255,255,0.7)' }
                }}
              />
            </ListItem>
          ))
        )}
      </List>

      <Divider sx={{ bgcolor: 'rgba(255,255,255,0.1)' }} />

      {/* Logout */}
      <Box sx={{ p: 2 }}>
        <Button
          fullWidth variant="outlined" color="error" startIcon={<Logout />}
          onClick={onLogout}
          sx={{
            borderColor: 'rgba(244,67,54,0.5)', color: '#f44336',
            '&:hover': { borderColor: '#f44336', bgcolor: 'rgba(244,67,54,0.1)' },
            textTransform: 'none',
          }}
        >
          退出登录
        </Button>
      </Box>

      {/* New Conversation Dialog */}
      <Dialog open={showNewDialog} onClose={() => setShowNewDialog(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          新建会话
          <IconButton size="small" onClick={() => setShowNewDialog(false)}><Close /></IconButton>
        </DialogTitle>
        <DialogContent>
          <TextField
            autoFocus fullWidth variant="outlined" label="会话标题"
            value={newTitle} onChange={e => setNewTitle(e.target.value)}
            onKeyPress={e => {
              if (e.key === 'Enter' && newTitle.trim()) {
                onCreateConversation(newTitle.trim());
                setNewTitle('');
                setShowNewDialog(false);
              }
            }}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowNewDialog(false)}>取消</Button>
          <Button variant="contained" onClick={() => {
            if (newTitle.trim()) {
              onCreateConversation(newTitle.trim());
              setNewTitle('');
              setShowNewDialog(false);
            }
          }}>创建</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
