# ServicePilot 项目进度

> 最后更新：2026-08-30
> 当前分支：`master`  
> 当前阶段：RAG 向量检索与引用回答代码、自动化测试已完成，待真实百炼 RAG 验证

## 项目目标

ServicePilot 是一个用于秋招作品集的智能客服 Agent 项目。当前采用模块化单体架构，先完成完整业务闭环，再逐步加入 LLM、Prompt、RAG、工具调用和人工审批；线程池、消息队列和微服务留到后期根据实际需求演进。

## 技术栈

- Java 21、Spring Boot 4.1.0
- Spring AI 2.0.0、Spring Modulith 2.1.0
- MyBatis-Plus 3.5.17、Flyway
- PostgreSQL 17 + pgvector、Docker Compose
- Vue 3 + TypeScript + Vite、pnpm
- Maven、Git、GitHub

## 工程结构

```text
D:\agent
├─ service-pilot-server    Spring Boot 后端
├─ service-pilot-web       Vue 前端
├─ docs                    项目文档
├─ compose.yml             PostgreSQL/pgvector 容器配置
├─ .env.example            环境变量示例
├─ .env                    本地真实配置，禁止提交
└─ AGENTS.md               Codex 长期协作规则
```

后端业务模块：

```text
com.servicepilot
├─ conversation            客服会话模块（已开发基础功能）
│  ├─ controller           HTTP 接口
│  ├─ service              业务逻辑
│  ├─ mapper               MyBatis-Plus 数据访问
│  ├─ domain               数据库实体和枚举
│  └─ dto                  请求与响应对象
├─ agent                   Agent 编排模块（已完成基础对话与多轮上下文）
├─ knowledge               RAG 知识库模块（已完成文档入库基础功能）
├─ order                   订单工具模块（待开发）
└─ config                  全局配置
```

### 开发阶段管理员认证

- 使用 Spring Security HTTP Basic 保护后台管理接口。
- 管理员账号和密码从 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 环境变量读取。
- 密码使用 BCrypt 编码后保存在进程内存中，不写入数据库、源码或 Git。
- 请求采用无状态认证，不创建登录 Session。
- `/api/knowledge/**` 仅允许 `ADMIN` 角色访问。
- `/api/conversations/**` 和 `/actuator/health` 保持匿名访问。

## 已完成内容

### 工程与数据层

- 建立 Spring Boot 后端和 Vue 前端工程。
- 使用 Java 21 运行后端。
- 使用 Docker Compose 运行 PostgreSQL 17 + pgvector。
- IDEA 和 DBeaver 已连接数据库 `service_pilot`。
- 使用 MyBatis-Plus Mapper 访问业务数据。
- 使用 Spring Modulith 定义 `conversation`、`agent`、`knowledge`、`order` 模块。
- 配置健康检查：`GET /actuator/health`。

Flyway 已执行到 V5：

```text
V1  初始化平台配置
V2  创建 Spring Modulith 事件记录表
V3  创建客服会话表和聊天消息表
V4  调整 Modulith JDBC 事件表结构
V5  创建知识文档表
```

主要数据表：

- `customer_session`：客户会话。
- `chat_message`：客户、AI、人工客服和系统消息。
- `platform_setting`：项目初始化配置。
- `event_publication`：Spring Modulith 事件记录。
- `flyway_schema_history`：Flyway 迁移历史。
- `vector_store`：后续 RAG 使用的向量表，由 Spring AI 初始化。
- `knowledge_document`：保存知识文档原文、向量化状态和切片数量。

### 客服会话接口

```http
POST /api/conversations
```

- 创建状态为 `WAITING` 的会话。
- 校验客户名称不能为空。

```http
POST /api/conversations/{sessionId}/messages
```

- 保存发送者为 `CUSTOMER` 的客户消息。
- 校验消息内容和会话是否存在。
- 已结束会话返回 HTTP 409。

```http
GET /api/conversations/{sessionId}/messages
```

- 按创建时间、消息 ID 升序查询聊天记录。
- 新会话返回空数组，不存在的会话返回 HTTP 404。

```http
PATCH /api/conversations/{sessionId}/close
```

- 将会话改为 `CLOSED` 并更新时间。
- 重复关闭保持幂等，关闭后禁止发送消息。

会话状态：`WAITING`、`ACTIVE`、`CLOSED`。  
消息发送者：`CUSTOMER`、`AGENT`、`AI`、`SYSTEM`。

### AI 自动回复接口

```http
POST /api/conversations/{sessionId}/chat
```

- `CustomerSupportAgent` 使用 Spring AI `ChatClient` 调用模型。
- 使用客服 System Prompt 约束回答风格并减少编造。
- 保存 `CUSTOMER` 消息和 `AI` 回复。
- 使用 `ChatReplyResponse` 返回客户消息、AI 回复和知识引用列表。
- 会话不存在返回 HTTP 404，已关闭返回 HTTP 409，空问题返回 HTTP 400。
- 模型未启用返回 HTTP 503，模型请求失败或空回复返回 HTTP 502。
- 从 `chat_message` 读取同一会话最近 20 条有效消息作为模型上下文。
- 将客户消息映射为 `USER`，将 AI/人工客服消息映射为 `ASSISTANT`。
- 数据库事务仅负责保存或读取消息，调用百炼时不保持数据库事务。
- 百炼调用失败时保留已经接收的客户消息，避免问题内容被事务回滚。
- 每次回答前根据本次问题从 `vector_store` 检索最多 3 个相似知识切片，相似度阈值为 0.70。
- 将检索结果加入 System Prompt，并要求模型优先依据知识回答、资料不足时不编造。
- 将知识文档 ID、标题、切片编号、原文和相似度作为 `references` 返回。
- 知识检索失败返回 HTTP 502，并保留已经接收的客户消息。

### RAG 知识文档入库接口

```http
POST /api/knowledge/documents
```

- 接收知识标题和原文，并保存到 `knowledge_document`。
- 使用 Spring AI `TokenTextSplitter` 将原文切成适合向量化的片段。
- 使用 `EmbeddingModel` 和 `VectorStore` 将切片写入 `vector_store`。
- 为每个切片保存知识文档 ID、标题、切片序号和来源类型等元数据。
- 入库成功将文档状态改为 `READY`，失败改为 `FAILED`。
- 调用外部向量模型时不保持数据库事务，避免长事务占用连接。
- 知识上传属于后台管理功能，接口要求身份认证，不匿名开放。
- 已接入开发阶段管理员 HTTP Basic 认证，可使用管理员账号进行真实入库测试。

## AI 配置状态

- 已创建阿里云百炼普通 API Key，真实值仅保存在本地 `.env`。
- `application-ai.yml` 通过 Spring AI 的 OpenAI 适配器连接百炼兼容接口。
- `AI_CHAT_MODEL` 控制聊天模型，实际值以本地 `.env` 为准。
- `AI_EMBEDDING_MODEL` 控制向量模型，计划使用 `text-embedding-v4`。
- IDEA 使用 Java 21，并启用 `ai` Profile。
- 2026-08-24 已验证 AI Profile 启动成功：数据库连接、Flyway V4、pgvector 初始化和 8080 端口均正常。
- 2026-08-25 已完成百炼真实请求验证：成功生成中文 AI 回复，并持久化 `CUSTOMER` 和 `AI` 两条消息。
- 2026-08-30 已完成百炼真实多轮验证：同一会话第二轮能够正确回忆第一轮提供的订单编号。
- 已确认本地 `vector_store.embedding` 为 1024 维，与 `text-embedding-v4` 默认维度一致。

需要的本地变量名称：

```env
AI_API_KEY=
AI_BASE_URL=
AI_CHAT_MODEL=
AI_EMBEDDING_MODEL=
SPRING_PROFILES_ACTIVE=ai
```

禁止在本文档、`.env.example`、代码、截图或 GitHub 中填写真实 API Key。

## 测试状态

后端集成测试使用 JUnit 5、Spring Boot Test、MockMvc、Testcontainers PostgreSQL/pgvector 和 Spring Modulith 结构校验。

当前测试类包含 32 个测试，覆盖：

- 上下文启动、Mapper 注入和 MyBatis-Plus 持久化。
- 创建会话、发送消息及参数和异常校验。
- 查询聊天记录、排序、空记录和会话不存在。
- 结束会话、重复结束、会话不存在和关闭后禁止发消息。
- AI 回复的双消息持久化、参数校验、会话不存在和关闭后禁止调用模型。
- 多轮历史消息按角色和时间顺序传递给 AI。
- 调用 AI 时不存在活动数据库事务，AI 失败后客户消息仍然保留。
- 知识文档创建、文本切片、向量写入和元数据保存。
- 空知识内容校验、向量写入失败状态以及匿名访问拒绝。
- 正确管理员账号允许访问、错误密码返回 401、非管理员用户返回 403。
- RAG 检索问题、Top 3、0.70 阈值、知识元数据映射和引用响应。
- 检索失败时保留客户消息，且不调用大模型。
- 将参考知识加入 Prompt，并防止执行知识内容中的角色命令或提示词。
- Spring Modulith 模块结构。

2026-08-30 使用 Java 21.0.9 执行全量测试：32 个测试全部通过，0 失败、0 错误；Flyway V5 在 Testcontainers PostgreSQL/pgvector 中成功执行。

2026-08-30 完成真实知识入库测试：管理员认证成功，知识文档状态为 `READY`、切片数量为 1，百炼 `text-embedding-v4` 生成 1024 维向量并成功写入 `vector_store`；元数据中的文档 ID、标题、来源类型和切片序号均正确。

2026-08-30 完成百炼真实多轮测试：同一 `sessionId` 连续对话，第二轮正确返回第一轮提供的订单编号；4 条 `CUSTOMER`/`AI` 消息均成功持久化。

```powershell
cd D:\agent\service-pilot-server
.\mvnw.cmd test
```

## 下一步开发

当前第一步：**使用已入库的退款知识进行一次真实 RAG 问答，确认回答内容和 `references`**。

已增强接口：

```http
POST /api/conversations/{sessionId}/chat
```

当前接口处理流程：

```text
接收客户问题并保存 CUSTOMER 消息
→ 使用问题生成向量并检索最多 3 个相似知识切片
→ 将聊天历史和参考知识交给 CustomerSupportAgent
→ 百炼基于参考知识生成回答
→ 保存 AI 消息
→ 返回客户消息、AI 回复和 references 引用列表
```

本阶段已实现内容：

- `KnowledgeDocument`：保存知识原文、状态和切片数量。
- `CreateKnowledgeRequest`、`KnowledgeDocumentResponse`：定义上传参数和返回结果。
- `KnowledgeDocumentMapper`：使用 MyBatis-Plus 读写知识文档。
- `KnowledgeDocumentService`：切分文本、写入向量并维护处理状态。
- `KnowledgeDocumentController`：提供需要身份认证的知识上传接口。
- Flyway V5：创建 `knowledge_document` 表。
- 自动化测试：验证成功入库、参数校验、向量写入失败和匿名访问拒绝。
- 管理员 HTTP Basic 认证：验证管理员身份后才允许上传知识。
- `KnowledgeRetriever`：定义知识模块公开的检索接口。
- `VectorKnowledgeRetriever`：使用 Spring AI `VectorStore` 执行相似度检索。
- `KnowledgeReference`、`KnowledgeReferenceResponse`：传递检索结果并返回引用来源。
- `CustomerSupportAgent`：将参考知识加入安全约束后的 System Prompt。
- `ConversationService`：在模型调用前检索知识，并在响应中返回引用列表。

真实多轮验证结果：

- 使用 IDEA 的 Java 21、`ai` Profile 和本地 `.env` 启动后端成功。
- 第一次通过 `/chat` 告诉 AI 订单编号，第二次使用同一 `sessionId` 询问。
- 百炼正确回复第一轮提供的订单编号，多轮上下文验证通过。
- 自动化测试不调用百炼、不消耗 Token；本次真实验证消耗少量免费额度。

真实向量入库验证已完成：`knowledge_document` 与 `vector_store` 数据正确，短文生成 1 个切片，`chunk_count = 1`、`chunk_index = 0`。

下一项验证：**重启后端后询问“定制商品支持七天无理由退款吗”，确认 AI 根据已入库知识回答，且 `references` 返回“七天无理由退款规则”**。

验证通过后的下一项独立功能：**订单查询工具调用**。

后续路线：RAG 文档入库 → 向量检索与引用回答 → 订单工具调用 → 人工审批/接管 → 前端界面 → 并发与微服务演进。

## Git 基线

当前 `master` 与 `origin/master` 均位于：

```text
5206dea 实现 RAG 知识入库与管理员认证
```

当前工作区仅包含尚未提交的 RAG 向量检索、引用回答、自动化测试和进度文档更新；32 个自动化测试已通过，真实 RAG 回答尚待验证。

此前主要提交：

```text
5206dea 实现 RAG 知识入库与管理员认证
07af909 实现 AI 客服多轮对话上下文
6c7731f 实现 AI 客服自动回复接口
3c7b6b5 新增 ServicePilot 项目进度记录
1170699 实现会话聊天记录查询接口
321ef78 文档：添加项目协作与 Git 提交规范
0091e9c 实现客户发送消息接口
61f9c8b 更改异常抛出
39edab7 实现创建客服会话接口
3b7b71f 重构使用MyBatis-Plus并初始化会话模块
d3cc0ad 新建客服会话表和消息表
6ef38aa 初始化 ServicePilot 项目
```

## 新对话继续开发

```text
请继续开发 D:\agent 下的 ServicePilot 项目。

开始前请先阅读：
1. D:\agent\AGENTS.md
2. D:\agent\README.md
3. D:\agent\docs\PROJECT_STATUS.md
4. Git 提交记录和当前未提交修改

先总结当前进度和下一步，不要重复实现已经完成的功能。
```

## 维护规则

- 每完成一个独立功能并验证通过后，更新已完成功能、测试状态、下一步开发和 Git 基线。
- 只记录已经确认的事实；未完成内容必须标记为“待开发”。
- 不记录密码、API Key 或其他敏感信息。
- 只有用户明确要求时才执行 Git 提交或推送。
