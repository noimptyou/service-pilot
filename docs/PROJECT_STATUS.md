# ServicePilot 项目进度

> 最后更新：2026-08-25  
> 当前分支：`master`  
> 当前阶段：AI 自动回复代码已完成，等待百炼真实调用验证

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
├─ agent                   Agent 编排模块（待开发）
├─ knowledge               RAG 知识库模块（待开发）
├─ order                   订单工具模块（待开发）
└─ config                  全局配置
```

## 已完成内容

### 工程与数据层

- 建立 Spring Boot 后端和 Vue 前端工程。
- 使用 Java 21 运行后端。
- 使用 Docker Compose 运行 PostgreSQL 17 + pgvector。
- IDEA 和 DBeaver 已连接数据库 `service_pilot`。
- 使用 MyBatis-Plus Mapper 访问业务数据。
- 使用 Spring Modulith 定义 `conversation`、`agent`、`knowledge`、`order` 模块。
- 配置健康检查：`GET /actuator/health`。

Flyway 已执行到 V4：

```text
V1  初始化平台配置
V2  创建 Spring Modulith 事件记录表
V3  创建客服会话表和聊天消息表
V4  调整 Modulith JDBC 事件表结构
```

主要数据表：

- `customer_session`：客户会话。
- `chat_message`：客户、AI、人工客服和系统消息。
- `platform_setting`：项目初始化配置。
- `event_publication`：Spring Modulith 事件记录。
- `flyway_schema_history`：Flyway 迁移历史。
- `vector_store`：后续 RAG 使用的向量表，由 Spring AI 初始化。

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
- 使用 `ChatReplyResponse` 同时返回两条消息。
- 会话不存在返回 HTTP 404，已关闭返回 HTTP 409，空问题返回 HTTP 400。
- 模型未启用返回 HTTP 503，模型请求失败或空回复返回 HTTP 502。

## AI 配置状态

- 已创建阿里云百炼普通 API Key，真实值仅保存在本地 `.env`。
- `application-ai.yml` 通过 Spring AI 的 OpenAI 适配器连接百炼兼容接口。
- `AI_CHAT_MODEL` 控制聊天模型，实际值以本地 `.env` 为准。
- `AI_EMBEDDING_MODEL` 控制向量模型，计划使用 `text-embedding-v4`。
- IDEA 使用 Java 21，并启用 `ai` Profile。
- 2026-08-24 已验证 AI Profile 启动成功：数据库连接、Flyway V4、pgvector 初始化和 8080 端口均正常。
- AI 自动回复接口已经实现并通过模拟模型测试；API Key 和百炼模型的真实请求仍需手动完成端到端验证。

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

当前测试类包含 20 个测试，覆盖：

- 上下文启动、Mapper 注入和 MyBatis-Plus 持久化。
- 创建会话、发送消息及参数和异常校验。
- 查询聊天记录、排序、空记录和会话不存在。
- 结束会话、重复结束、会话不存在和关闭后禁止发消息。
- AI 回复的双消息持久化、参数校验、会话不存在和关闭后禁止调用模型。
- Spring Modulith 模块结构。

2026-08-25 使用 Java 21 执行全量测试：20 个测试全部通过，0 失败、0 错误。

```powershell
cd D:\agent\service-pilot-server
.\mvnw.cmd test
```

## 下一步开发

当前第一步：**完成百炼真实调用的端到端验证**。

已实现接口：

```http
POST /api/conversations/{sessionId}/chat
```

接口处理流程：

```text
接收客户问题
→ 校验会话并保存 CUSTOMER 消息
→ CustomerSupportAgent 通过 Spring AI 调用百炼
→ 保存 AI 消息
→ 返回客户消息和 AI 回复
```

已实现内容：

- `agent/CustomerSupportAgent`：封装 `ChatClient` 和客服 System Prompt。
- `conversation/dto/ChatReplyResponse`：返回客户消息和 AI 消息。
- `ConversationService` 和 `ConversationController`：编排并提供 `/chat` 接口。
- 模型模拟测试：验证双消息持久化、参数校验、会话不存在和关闭状态。

真实验证步骤：

- 使用 IDEA 的 Java 21、`ai` Profile 和本地 `.env` 启动后端。
- 创建未关闭的客服会话并调用 `/chat`。
- 确认百炼返回中文回复，数据库保存 `CUSTOMER` 和 `AI` 两条消息。
- 自动化测试不调用百炼、不消耗 Token；真实验证会消耗少量免费额度。

真实调用通过后的下一项独立功能：**多轮对话上下文**。

后续路线：多轮上下文 → Prompt 完善 → RAG 知识库 → 订单工具调用 → 人工审批/接管 → 前端界面 → 并发与微服务演进。

## Git 基线

创建本文档前，`master` 与 `origin/master` 均位于：

```text
71af084 实现结束客服会话接口
```

当前工作区包含尚未提交的 AI 自动回复代码、自动化测试和进度文档更新；完成百炼真实调用验证后再提交。

此前主要提交：

```text
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
