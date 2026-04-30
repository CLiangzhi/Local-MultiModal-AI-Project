# 本地多模态 AI 助手

## 项目简介

本地多模态 AI 助手是一个基于本地大语言模型的多功能智能助手系统，支持文本对话、图像识别、语音识别和视频分析等多种交互方式。系统采用前后端分离架构，后端使用 Spring Boot，前端使用 React，并集成了 Ollama 本地大模型、RAG（检索增强生成）知识库和多模态处理能力。

### 主要功能

1. **文本对话**：基于 Qwen2.5 模型的智能对话功能，支持流式输出
2. **图像识别**：基于 LLaVA 模型的图像理解和分析功能
3. **语音识别**：集成 Whisper 进行语音转文字，支持中文语音识别
4. **视频分析**：结合图像识别和语音识别，对视频内容进行综合分析
5. **知识库管理**：支持上传 PDF 和 Word 文档，构建个人专属知识库，实现 RAG 检索增强
6. **用户认证**：基于 JWT 的用户登录认证系统
7. **历史记录**：保存用户对话历史，支持跨会话查询

## 环境要求

### 硬件要求

- CPU：建议 4 核心及以上
- 内存：建议 8GB 及以上
- 硬盘：至少 20GB 可用空间（用于存放模型和数据）

### 软件要求

- 操作系统：Windows（推荐 Windows 10 或更高版本）
- Java：JDK 17
- Node.js：建议 v16 或更高版本
- Maven：建议 3.6 或更高版本
- MySQL：建议 8.0 或更高版本
- Python：建议 3.8 或更高版本（用于安装 Whisper）

## 安装与配置

### 1. 安装 Ollama

1. 访问 [Ollama 官网](https://ollama.com/) 下载并安装 Ollama
2. 安装完成后，打开命令行，执行以下命令下载所需模型：
   ```bash
   ollama pull qwen2.5:7b
   ollama pull llava:7b
   ```
3. 确保 Ollama 服务已启动（默认运行在 http://localhost:11434）

### 2. 安装 Whisper（用于语音识别）

1. 确保已安装 Python 3.8 或更高版本
2. 在命令行中执行以下命令安装 Whisper：
   ```bash
   pip install openai-whisper
   ```
3. 安装 FFmpeg（Whisper 依赖）：
   - Windows：下载 FFmpeg 并将其添加到系统 PATH 环境变量中
   - 下载地址：https://ffmpeg.org/download.html

### 3. 安装 MySQL

1. 下载并安装 MySQL 8.0 或更高版本
2. 使用 Navicat 或其他 MySQL 管理工具创建数据库
3. 导入数据库初始化脚本（脚本文件名：`database_init.sql`，需自行替换为实际文件名）
4. 记录数据库连接信息（主机、端口、用户名、密码）

### 4. 配置后端

1. 进入 `backend` 目录
2. 修改 `src/main/resources/application.properties` 文件，配置数据库连接信息：
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/local_ai_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
   spring.datasource.username=root
   spring.datasource.password=your_password  # 替换为你的数据库密码
   spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
   ```
3. 如需修改数据库名称，需同时修改上述配置文件中的 `local_ai_db` 为新的数据库名称

### 5. 配置前端

1. 进入 `frontend` 目录
2. 执行以下命令安装依赖：
   ```bash
   npm install
   ```

## 启动步骤

### 1. 启动 Ollama

确保 Ollama 服务已启动（安装后默认启动）

### 2. 启动后端

1. 进入 `backend` 目录
2. 执行以下命令启动后端服务：
   ```bash
   mvn spring-boot:run
   ```
3. 等待服务启动完成（默认运行在 http://localhost:8080）

### 3. 启动前端

1. 进入 `frontend` 目录
2. 执行以下命令启动前端服务：
   ```bash
   npm start
   ```
3. 浏览器会自动打开 http://localhost:3000

## 使用说明

### 1. 用户登录

- 默认账号：admin
- 默认密码：123456

### 2. 文本对话

1. 在输入框中输入问题或指令
2. 点击发送按钮或按回车键发送
3. 系统将基于 Qwen2.5 模型生成回答

### 3. 图像识别

1. 点击附件按钮上传图片
2. 输入相关问题（可选）
3. 系统将基于 LLaVA 模型分析图片并生成回答

### 4. 语音识别

1. 点击附件按钮上传音频文件
2. 输入相关问题（可选）
3. 系统将使用 Whisper 识别语音内容，并基于 Qwen2.5 模型生成回答

### 5. 视频分析

1. 点击附件按钮上传视频文件
2. 输入相关问题（可选）
3. 系统将提取视频关键帧和音频，综合分析后生成回答

### 6. 知识库管理

1. 在右侧知识库面板点击上传按钮
2. 选择 PDF 或 Word 文档上传
3. 系统将自动对文档进行向量化处理
4. 在对话中提问时，系统会自动从知识库中检索相关信息增强回答

## 项目结构

```
Local-MultiModal-AI-Project/
├── backend/                      # 后端项目目录
│   ├── pom.xml                   # Maven 项目配置文件
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/ai/
│           │       ├── Application.java          # 应用程序入口
│           │       ├── config/                  # 配置类
│           │       │   ├── JwtFilter.java       # JWT 过滤器
│           │       │   └── SecurityConfig.java  # 安全配置
│           │       ├── controller/              # 控制器层
│           │       │   ├── AuthController.java  # 认证控制器
│           │       │   └── ChatController.java  # 聊天控制器
│           │       ├── entity/                  # 实体类
│           │       │   ├── ChatMessageEntity.java  # 聊天消息实体
│           │       │   └── UserEntity.java        # 用户实体
│           │       ├── repository/              # 数据访问层
│           │       │   ├── ChatMessageRepository.java  # 聊天消息仓库
│           │       │   └── UserRepository.java        # 用户仓库
│           │       ├── service/                 # 服务层
│           │       │   ├── KnowledgeBaseService.java  # 知识库服务
│           │       │   ├── OllamaService.java         # Ollama 服务
│           │       │   └── RagService.java             # RAG 服务
│           │       └── util/                    # 工具类
│           │           └── JwtUtil.java         # JWT 工具类
│           └── resources/
│               ├── application.properties      # 应用配置文件
│               └── application.yml             # YAML 配置文件
├── frontend/                     # 前端项目目录
│   ├── package.json              # Node.js 项目配置文件
│   ├── public/
│   │   └── index.html            # HTML 入口文件
│   └── src/
│       ├── App.js                # 主应用组件
│       ├── Login.js              # 登录组件
│       └── index.js              # React 入口文件
├── local_data/                   # 本地数据目录
│   └── knowledge_base/           # 知识库存储目录
│       ├── user_test_001/        # 用户1的知识库
│       └── user_test_002/        # 用户2的知识库
└── README.md                     # 项目说明文档
```

## 主要组件说明

### 后端组件

- **Application.java**：Spring Boot 应用程序入口，配置 WebClient Bean
- **AuthController.java**：处理用户登录认证，生成 JWT Token
- **ChatController.java**：处理聊天请求，包括文本对话、图像识别、语音识别和视频分析
- **OllamaService.java**：封装与 Ollama 服务的交互，提供流式对话和图像分析功能
- **RagService.java**：实现 RAG 功能，包括文档向量化、相似度检索
- **KnowledgeBaseService.java**：管理知识库文件，处理文件上传和列表查询
- **JwtFilter.java**：JWT 认证过滤器，验证请求中的 Token
- **SecurityConfig.java**：Spring Security 配置，定义安全规则和认证方式

### 前端组件

- **App.js**：主应用组件，实现聊天界面、文件上传、知识库管理等功能
- **Login.js**：登录组件，处理用户登录逻辑
- **index.js**：React 应用入口，渲染主应用组件

## 技术栈

### 后端技术

- Spring Boot 3.2.0
- Spring Security（JWT 认证）
- Spring WebFlux（流式响应）
- Spring Data JPA（数据持久化）
- LangChain4j（RAG 功能）
- MySQL（数据库）

### 前端技术

- React 18.2.0
- Material-UI（UI 组件库）
- Axios（HTTP 客户端）
- React Markdown（Markdown 渲染）

### AI 模型与工具

- Ollama（本地大模型运行环境）
- Qwen2.5:7b（文本生成模型）
- LLaVA:7b（视觉语言模型）
- Whisper（语音识别模型）
- BGE-Small-ZH（中文文本嵌入模型）
- FFmpeg（音视频处理工具）

## 注意事项

1. 确保所有依赖服务（Ollama、MySQL）已正确安装并运行
2. 首次启动后端时，JPA 会自动创建数据库表结构
3. 上传大文件可能需要较长时间，请耐心等待
4. 知识库向量化过程在后台异步执行，可能需要一定时间
5. 系统默认支持 PDF 和 Word 文档的知识库功能

## 许可证

本项目仅供学习和研究使用。
