import React from 'react';
import { Box, Typography, IconButton, Tooltip, Chip } from '@mui/material';
import { Edit, CallSplit, Build } from '@mui/icons-material';
import ReactMarkdown from 'react-markdown';

export default function MessageBubble({ msg, onEdit, onBranchSwitch, branchOptions }) {
  const isUser = msg.role === 'user';
  const isAgent = msg.role === 'agent';
  const hasToolCalls = msg.toolCalls && msg.toolCalls.length > 0;

  // Check if this is an agent thinking / tool message
  const isAgentThinking = msg.role === 'assistant' && msg.content && msg.content.includes('[Agent模式对话]');
  const isSystem = msg.role === 'tool';

  const bubbleStyles = {
    p: 2,
    borderRadius: 2,
    maxWidth: '82%',
    wordBreak: 'break-word',
    ...(isUser ? {
      bgcolor: '#1976d2',
      color: '#fff',
      alignSelf: 'flex-end',
      boxShadow: '0 1px 3px rgba(0,0,0,0.12)',
    } : isSystem ? {
      bgcolor: '#fff3e0',
      color: '#e65100',
      alignSelf: 'flex-start',
      border: '1px solid #ffe0b2',
      fontSize: '0.85em',
    } : {
      bgcolor: '#f8f9fa',
      color: '#111827',
      alignSelf: 'flex-start',
      border: '1px solid #e5e7eb',
    }),
    '& img': { maxWidth: '100%', height: 'auto' },
    '& p': { margin: 0, marginBottom: '0.8em', lineHeight: 1.6 },
    '& ul, & ol': { marginTop: '0.5em', marginBottom: '0.5em', paddingLeft: '1.5em' },
    '& li': { marginBottom: '0.3em' },
    '& pre': { background: isUser ? 'rgba(255,255,255,0.15)' : '#f0f0f0', padding: '10px', borderRadius: '4px', overflowX: 'auto', maxWidth: '100%' },
    '& code': { background: isUser ? 'rgba(255,255,255,0.2)' : '#f0f0f0', padding: '2px 4px', borderRadius: '4px', color: isUser ? '#fff' : '#d32f2f', wordBreak: 'break-word' },
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', mb: 2.5 }}>
      <Box sx={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start', mb: 0.5 }}>
        <Box sx={bubbleStyles}>
          {/* Branch indicator */}
          {msg.hasBranches && (
            <Chip
              icon={<CallSplit sx={{ fontSize: 14 }} />}
              label="分支"
              size="small"
              sx={{ mb: 1, bgcolor: isUser ? 'rgba(255,255,255,0.2)' : '#e3f2fd', color: isUser ? '#fff' : '#1976d2' }}
            />
          )}

          {/* Tool calls display */}
          {hasToolCalls && (
            <Box sx={{ mb: 1, p: 1, bgcolor: 'rgba(255,152,0,0.08)', borderRadius: 1, border: '1px solid rgba(255,152,0,0.2)' }}>
              <Typography variant="caption" sx={{ fontWeight: 'bold', color: '#e65100', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <Build sx={{ fontSize: 14 }} /> 工具调用记录
              </Typography>
              {msg.toolCalls.map((tc, i) => (
                <Box key={i} sx={{ mt: 0.5, fontSize: '0.8em', color: '#555' }}>
                  <div>🔧 <b>{tc.name}</b></div>
                  <div style={{ fontSize: '0.9em', whiteSpace: 'pre-wrap', maxHeight: '100px', overflowY: 'auto', background: '#f5f5f5', padding: '4px 8px', borderRadius: 4, marginTop: 4 }}>
                    {tc.result || '执行中...'}
                  </div>
                </Box>
              ))}
            </Box>
          )}

          {/* Agent thinking indicator */}
          {isAgentThinking && (
            <Chip
              icon={<Build sx={{ fontSize: 14 }} />}
              label="Agent 模式"
              size="small"
              sx={{ mb: 1, bgcolor: '#fff3e0', color: '#e65100' }}
            />
          )}

          {/* Message content */}
          {isSystem ? (
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{msg.content}</Typography>
          ) : (
            <ReactMarkdown>{msg.content || ''}</ReactMarkdown>
          )}
        </Box>
      </Box>

      {/* Action buttons */}
      <Box sx={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start', gap: 0.5, px: 1 }}>
        {isUser && onEdit && (
          <Tooltip title="编辑并创建分支">
            <IconButton size="small" onClick={() => onEdit(msg)} sx={{ opacity: 0.5, '&:hover': { opacity: 1 } }}>
              <Edit sx={{ fontSize: 14 }} />
            </IconButton>
          </Tooltip>
        )}
        {msg.hasBranches && branchOptions && branchOptions.length > 0 && (
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
            {branchOptions.map((br, i) => (
              <Chip
                key={i}
                label={`分支 ${i + 1}: ${(br.preview || '').substring(0, 20)}...`}
                size="small"
                onClick={() => onBranchSwitch && onBranchSwitch(br.messageId)}
                sx={{ cursor: 'pointer', bgcolor: '#e8f0fe', '&:hover': { bgcolor: '#d2e3fc' } }}
              />
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
}
