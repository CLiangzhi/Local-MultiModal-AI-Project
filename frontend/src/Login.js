import React, { useState } from 'react';
import { Box, Paper, TextField, Button, Typography, Alert } from '@mui/material';

function Login({ onLoginSuccess }) {
    const [form, setForm] = useState({ username: '', password: '' });
    const [error, setError] = useState('');

    const handleSubmit = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(form)
            });
            const data = await res.json();
            if (res.ok) {
                // 登录成功，把令牌存进浏览器的保险柜里
                localStorage.setItem('token', data.token);
                localStorage.setItem('username', data.username);
                onLoginSuccess(); // 通知 App 切换页面
            } else {
                setError(data.message || '登录失败');
            }
        } catch (e) { setError('连接后端服务失败'); }
    };

    return (
        <Box sx={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#f0f2f5' }}>
            <Paper elevation={3} sx={{ p: 4, width: 350, textAlign: 'center', borderRadius: 4 }}>
                <Typography variant="h5" fontWeight="bold" sx={{ mb: 3 }}>AI 商业智能台 - 登录</Typography>
                {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
                <TextField fullWidth label="用户名" variant="outlined" sx={{ mb: 2 }} onChange={e => setForm({...form, username: e.target.value})} />
                <TextField fullWidth label="密码" type="password" variant="outlined" sx={{ mb: 3 }} onChange={e => setForm({...form, password: e.target.value})} />
                <Button fullWidth variant="contained" size="large" onClick={handleSubmit} sx={{ bgcolor: '#1c2536' }}>登录</Button>
            </Paper>
        </Box>
    );
}

export default Login;