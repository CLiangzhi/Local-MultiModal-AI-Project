import React, { useRef, useState } from 'react';
import { Box, Typography, List, ListItem, ListItemText, ListItemIcon,
  IconButton, Divider, CircularProgress, Tooltip, Button, Dialog,
  DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { FolderShared, UploadFile, Delete, Refresh, Warning } from '@mui/icons-material';

const API = 'http://localhost:8080';

export default function KnowledgePanel({ files, onUpload, uploading, onFileDeleted }) {
  const kbInputRef = useRef(null);
  const [deleteDialog, setDeleteDialog] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);

  const handleDelete = async (filename) => {
    setDeleting(true);
    try {
      const res = await fetch(API + '/api/knowledge/files/' + encodeURIComponent(filename), {
        method: 'DELETE',
        headers: { 'Authorization': 'Bearer ' + localStorage.getItem('token') }
      });
      if (res.ok && onFileDeleted) onFileDeleted();
    } catch (e) { console.error('删除失败', e); }
    setDeleting(false);
    setDeleteDialog(null);
  };

  const handleRebuild = async () => {
    setRebuilding(true);
    try {
      await fetch(API + '/api/knowledge/rebuild', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + localStorage.getItem('token') }
      });
      if (onFileDeleted) setTimeout(onFileDeleted, 2000);
    } catch (e) { console.error('重建失败', e); }
    setRebuilding(false);
  };

  return (
    <Box sx={{
      width: 280, height: '100vh', display: 'flex', flexDirection: 'column',
      bgcolor: '#fff', borderLeft: '1px solid #e0e0e0', flexShrink: 0,
    }}>
      {/* Header */}
      <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="subtitle1" fontWeight="bold">私有知识库 (RAG)</Typography>
        <Box sx={{ display: 'flex', gap: 0.5 }}>
          <input type="file" hidden ref={kbInputRef} onChange={onUpload}
            accept=".pdf,.doc,.docx" />
          <Tooltip title="上传文档">
            <IconButton size="small" color="primary"
              onClick={() => kbInputRef.current.click()} disabled={uploading}>
              {uploading ? <CircularProgress size={18} /> : <UploadFile />}
            </IconButton>
          </Tooltip>
          <Tooltip title="重建索引">
            <IconButton size="small" onClick={handleRebuild} disabled={rebuilding}>
              {rebuilding ? <CircularProgress size={18} /> : <Refresh />}
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      <Divider />

      {/* File list */}
      <List sx={{ px: 1, overflowY: 'auto', flex: 1 }}>
        {files.length === 0 ? (
          <Box sx={{ p: 3, textAlign: 'center', color: '#aaa' }}>
            <Typography variant="caption" display="block">
              暂无上传文档
            </Typography>
            <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
              上传 PDF/Word 构建私有知识库
            </Typography>
          </Box>
        ) : (
          files.map((file, i) => {
            const name = typeof file === 'string' ? file : file.name || file;
            return (
              <ListItem key={i} sx={{ bgcolor: '#f4f6f8', borderRadius: 1, mb: 1 }}
                secondaryAction={
                  <IconButton size="small" onClick={() => setDeleteDialog(name)}
                    sx={{ color: '#999', '&:hover': { color: '#f44336' } }}>
                    <Delete sx={{ fontSize: 16 }} />
                  </IconButton>
                }>
                <ListItemIcon sx={{ minWidth: 36 }}>
                  <FolderShared fontSize="small" color="action" />
                </ListItemIcon>
                <ListItemText
                  primary={name}
                  primaryTypographyProps={{ noWrap: true, variant: 'body2' }}
                />
              </ListItem>
            );
          })
        )}
      </List>

      {/* Delete confirmation dialog */}
      <Dialog open={!!deleteDialog} onClose={() => setDeleteDialog(null)}>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Warning color="warning" /> 确认删除
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            删除 <b>{deleteDialog}</b> 后，相关向量索引也会被清除。此操作不可撤销。
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialog(null)}>取消</Button>
          <Button variant="contained" color="error"
            onClick={() => handleDelete(deleteDialog)} disabled={deleting}>
            {deleting ? '删除中...' : '确认删除'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
