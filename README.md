# NovaOps

NovaOps 是一个面向企业运维场景的运维管理与 RAG 智能问答平台。项目覆盖认证授权、用户管理、工单、资产、知识库、Agent 对话和数据看板，并支持前端 Mock 演示与 Java 真实后端联调。

## 核心能力

- 单租户统一存储：用户、权限、业务数据、知识库向量和 Agent 会话均在同一租户上下文中管理，按身份与权限码控制访问。
- 单身份权限：每个用户绑定一个身份，管理员可维护用户状态、身份和密码。
- 动态权限：后端下发菜单与权限码，前端实现动态路由和按钮级鉴权。
- 知识库：支持文档上传、Apache Tika 异步解析、重叠分块、向量化、分块预览、替换与删除。
- 可信问答：问题先由受控 Router 分流到 METADATA / RAG / CHAT；RAG 路径具备查询改写、相关性过滤、真实 chunk 证据、grounding 与 chunkId 引用完整性校验。
- 运维业务：提供工单、资产、Dashboard 等常见中后台模块。
- 任务型智能体（Agent 工作台）：受控 Plan-and-Act 循环 + 可扩展工具集 + 写操作人工确认 + 全链路审计，并通过 MCP 双向开放/接入外部工具。
- 双运行模式：可完全使用 MSW Mock，也可连接 Spring Boot、MySQL、Qdrant 与 SiliconFlow。
- 主题与布局：支持亮色/暗色主题、紧凑模式、动态菜单、多标签页和全局 Agent 浮窗。

## 技术栈

### 前端

- Vue 3、TypeScript、Vite
- Ant Design Vue、Pinia、Vue Router
- Axios、ECharts、MSW
- ESLint、Prettier、Husky、lint-staged、commitlint

### 后端与 AI

- Java 17、Spring Boot 3.4、Spring AI
- MyBatis、MySQL 8、JWT
- Apache Tika、Qdrant
- SiliconFlow OpenAI 兼容接口

## 项目结构

```text
NovaOps/
├─ backend/
│  ├─ sql/                         # 初始化与升级脚本
│  └─ src/main/
│     ├─ java/com/novaops/backend/
│     │  ├─ auth/                  # 认证、用户与权限
│     │  ├─ ticket/                # 工单
│     │  ├─ kb/                    # 文档解析、分块与检索
│     │  ├─ agent/                 # RAG 对话与会话
│     │  └─ common/                # 响应、异常与安全上下文
│     └─ resources/mapper/         # MyBatis 映射
├─ src/
│  ├─ api/                         # 接口客户端
│  ├─ components/                  # 通用组件与 Agent 浮窗
│  ├─ composables/                 # 对话等组合式逻辑
│  ├─ layout/                      # 后台布局
│  ├─ mocks/                       # MSW 数据与处理器
│  ├─ router/                      # 静态和动态路由
│  ├─ store/                       # 用户、主题、标签页、对话状态
│  ├─ views/                       # 页面模块
│  └─ utils/                       # 请求、SSE 等工具
├─ docker-compose.yml              # 一键启动 MySQL + Qdrant + 后端 + 前端
├─ Dockerfile                      # 前端镜像（Node 构建 + Nginx 托管）
├─ docker/nginx.conf               # Nginx：SPA 回退、/api 反代、SSE 关闭缓冲
├─ backend/Dockerfile              # 后端镜像（Maven 构建 + JRE 运行）
└─ .env.example                    # 环境变量示例
```

## 环境要求

- Node.js 20+ 与 npm
- Java 17 与 Maven 3.9+
- MySQL 8
- Docker 与 Docker Compose（一键整体启动时需要；手动模式下仅 Qdrant 需要 Docker）
- SiliconFlow API Key（知识库向量化和 RAG 问答时需要）

## 快速开始

### 方式一：前端 Mock 演示

```bash
npm install
npm run dev
```

默认访问地址：`http://127.0.0.1:5173/login`。

`.env.development` 可配置：

```dotenv
VITE_API_BASE_URL=/api
VITE_ENABLE_MOCK=full
```

`VITE_ENABLE_MOCK` 支持：

- `full`：所有接口使用 MSW。
- `partial`：已实现的接口访问 Java 后端，其余接口由 MSW 接管。
- `off`：关闭 MSW，所有 `/api` 请求访问真实后端。

预置账号密码均为 `123456`：

| 账号 | 身份 | 用途 |
| --- | --- | --- |
| `admin` | 管理员 | 全部功能及用户管理 |
| `staff` | 运维人员 | 工单、资产和智能问答 |
| `guest` | 访客 | 授权范围内只读访问 |

新账号通过邮箱注册并激活，默认授予普通成员身份；管理员账号请通过初始化脚本创建（默认 `admin / 123456`）。

### 方式二：一条命令容器启动（MySQL + Qdrant + 后端 + 前端）

```bash
cp .env.docker.example .env    # PowerShell: Copy-Item .env.docker.example .env
# 编辑 .env，至少填入 NOVAOPS_JWT_SECRET（≥32 字节随机值）
docker compose up -d
```

首次启动会构建前后端镜像，并在 MySQL 数据卷首次创建时自动导入 `backend/sql/novaops_init.sql`，完成后访问：

- 前端：`http://127.0.0.1:8088`
- 后端：`http://127.0.0.1:8090`
- Qdrant：`http://127.0.0.1:6333`
- MySQL：`127.0.0.1:13306`（root / `.env` 中的 `NOVAOPS_DB_PASSWORD`）

端口、密钥与 SiliconFlow Key 均通过 `.env` 配置，所有发布端口默认只绑定 `127.0.0.1`；`docker compose down -v` 可清空数据重新初始化。完整命令、排障与镜像结构见 [`docs/DOCKER_GUIDE.md`](docs/DOCKER_GUIDE.md)。

### 方式三：手动运行完整服务

#### 1. 初始化数据库

创建数据库并导入完整初始化脚本：

```sql
CREATE DATABASE novaops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

```bash
mysql -uroot -p novaops < backend/sql/novaops_init.sql
```

如果数据库来自旧版本，再执行：

```bash
mysql -uroot -p novaops < backend/sql/refactor_rag_migration.sql
mysql -uroot -p novaops < backend/sql/add_agent_chat_execution_audit.sql
```

#### 2. 启动 Qdrant

```bash
docker compose up -d qdrant
```

Qdrant HTTP 与 gRPC 默认端口分别为 `6333` 和 `6334`。

#### 3. 配置环境变量

至少配置数据库、JWT 和 SiliconFlow 密钥。`NOVAOPS_JWT_SECRET` 没有默认值，缺失或长度不足 32 字节时后端会拒绝启动。PowerShell 示例：

```powershell
$env:NOVAOPS_DB_URL="jdbc:mysql://127.0.0.1:3306/novaops?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8"
$env:NOVAOPS_DB_USERNAME="root"
$env:NOVAOPS_DB_PASSWORD="你的数据库密码"
$env:NOVAOPS_JWT_SECRET="请替换为足够长的随机密钥"
$env:SILICONFLOW_API_KEY="你的 API Key"
```

可选配置：

```dotenv
SILICONFLOW_BASE_URL=https://api.siliconflow.cn
SILICONFLOW_CHAT_MODEL=Qwen/Qwen3-8B
SILICONFLOW_EMBEDDING_MODEL=BAAI/bge-m3
QDRANT_BASE_URL=http://localhost:6333
QDRANT_COLLECTION=novaops_kb
NOVAOPS_KB_STORAGE=./data/kb
NOVAOPS_AGENT_TOP_K=5
NOVAOPS_AGENT_MIN_SCORE=0.55
NOVAOPS_AGENT_RETRIEVAL_VALIDATION_MIN_SCORE=0.5
NOVAOPS_AGENT_METADATA_MAX_DOCUMENTS=200
NOVAOPS_TICKET_STORAGE=./data/attachments
NOVAOPS_TICKET_MAX_FILE_BYTES=20971520
```

知识库分片上传的临时分片落在 `NOVAOPS_KB_STORAGE/_uploads/`，工单附件落在 `NOVAOPS_TICKET_STORAGE`；两者都是本地运行时目录，已在 `.gitignore` 中忽略，容器化部署时需要挂载出去。

不要提交真实密码、API Key 或本地配置文件。

#### 4. 启动后端

```bash
cd backend
mvn -gs mvn-settings.xml spring-boot:run
```

后端默认地址：`http://127.0.0.1:8090`。

#### 5. 启动前端

```bash
npm install
npm run dev
```

Vite 会将 `/api` 代理到 `http://127.0.0.1:8090`。

## 主要接口

### 认证与用户

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/register`
- `GET /api/auth/verify`
- `GET /api/auth/me`
- `GET /api/auth/menu`
- `GET /api/auth/roles`
- `GET /api/auth/users`
- `PUT /api/auth/users/{id}/status`
- `PUT /api/auth/users/{id}/role`
- `PUT /api/auth/users/{id}/password`

### 知识库与 Agent

- `GET/POST /api/kb/documents`
- `GET/PUT/DELETE /api/kb/documents/{id}`
- `GET /api/kb/documents/{id}/chunks`
- `POST /api/kb/documents/{id}/replace`
- `POST /api/agent/chat`（SSE）
- `GET /api/agent/conversations`
- `GET /api/agent/conversations/{id}`
- `POST /api/agent/tasks`（任务型智能体）
- `POST /api/agent/tasks/{id}/stream`（SSE）
- `POST /api/agent/tasks/{id}/confirm` / `POST /api/agent/tasks/{id}/cancel`
- `GET /api/agent/tasks` / `GET /api/agent/tasks/{id}`
- `POST /api/mcp`（MCP Server，默认禁用）

#### 任务型智能体（Agent 工作台）

在受控问答之外，项目提供任务型智能体：用户下达运维目标，Agent 在**用户权限范围内**、**业务状态机约束内**、**全链路审计**下规划并执行。

- 范式：受控 Plan-and-Act——模型负责"计划与总结"，代码负责"派发、校验、限额"；步骤失败/空结果触发事件驱动的有界重规划（默认 ≤2 次，总步数 ≤10）。
- 工具：`ticket.search/detail/assign/transfer/close/comment`、`asset.search`、`kb.search/metadata`，可动态扩展（实现 `AgentToolExecutor` 即自动注册）；远端 MCP 工具桥接后以 `mcp_<server>_<tool>` 出现。
- 写操作两段式确认：WRITE 工具先返回效果预览（不落库），用户在前端确认后才真正执行；状态机与动作级权限由 Service 层强制。
- 审计：任务、步骤流水与每一次工具调用（含越权拦截）均落库（`agent_task` / `agent_task_step` / `agent_audit_log`）。
- 运行模型：引擎循环护栏（步数/重规划/观察截断/单步超时）见 `app.agent.task.*` 配置。任务会话不跨重启保留，但任务与审计记录已持久化可追溯。
- 线程池：任务主循环与工具执行使用两个独立池（`nova-agent-*` / `nova-agent-tool-*`），避免任务线程阻塞等工具时双重占坑；工具池满载时快速拒绝并由引擎降级为单步失败重规划，配置见 `app.agent.executor-*` 与 `app.agent.tool-executor-*`。
- MCP Server：`POST /api/mcp`（JSON-RPC 2.0，协议版本 2025-03-26）。设置 `NOVAOPS_AGENT_MCP_SERVER_TOKEN` 后启用；仅暴露只读工具，外部宿主（如 Claude Desktop、IDE Agent）以 `Authorization: Bearer <token>` 接入。
- MCP Client（接入外部工具，如 Tavily）：

```yaml
app:
  agent:
    mcp:
      remote:
        servers:
          - name: tavily
            endpoint: https://mcp.tavily.com/mcp
            token: ${TAVILY_API_KEY:}
            enabled: ${TAVILY_MCP_ENABLED:false}
```

详见 `docs/AGENT_MODULE_PLAN.md`（设计）与 `docs/AGENT_MODULE_PROGRESS.md`（验收记录）。

Agent 对话会先返回受控路由结果（`route` 事件），并根据路由确定性生成可公开展示的行动计划（`plan` 事件），再执行真实的处理管线；展示计划不再额外调用模型。路由器只接受版本化 JSON，支持 `METADATA`、`RAG`、`CHAT`、`CLARIFY`、`REJECT`；置信度不足或路由模型不可用时不会默认执行检索，而是返回澄清提示。`METADATA` 直接根据数据库元数据生成列表、详情和统计，不执行向量检索或回答模型；`CHAT` 仅处理路由器明确识别的合理通用对话。RAG 没有有效证据时不会调用回答模型，回答未通过 grounding / chunkId 完整性校验时会安全拒答；会话首轮没有可补全的指代时也会跳过检索词改写调用。路由、候选 chunk、有效 chunk、引用和校验结果会写入 `agent_message.execution_json`。任务型 Agent 的真实 Plan-and-Act 由独立引擎处理，不受对话展示计划调整影响。

`POST /api/agent/chat` 的 SSE 事件如下：

| 事件 | 用途 |
| --- | --- |
| `route` | 受控路由结果、意图、置信度、检索词与项目支持的过滤条件 |
| `plan` | 一次性下发 `steps`，包含 `action/label/query/reason/status`，已按路由裁剪 |
| `step` | 按 `action` 更新执行状态：`running/done/failed`，结果摘要放在 `payload` |
| `delta` | 回答正文增量 |
| `citation` | 知识库引用列表 |
| `evidence` | 通过校验的原始检索证据 |
| `meta` | 检索与校验统计、依据校验结果与耗时 |
| `done` | 本轮正常完成 |
| `error` | 本轮失败；包括建会话等流建立前的同步异常 |

该接口必须返回 `text/event-stream`。前端发现 HTTP 200 携带普通 JSON 错误体时，会读取其中的 `message` 并显示错误，不再产生空白 AI 气泡。

### 工单

- `GET/POST /api/tickets`
- `GET/PUT /api/tickets/{id}`
- `POST /api/tickets/{id}/actions`
- `GET/POST /api/tickets/{id}/comments`
- `POST /api/tickets/{id}/attachments`

## 常用命令

```bash
npm run dev
npm run build
npm run lint
npm run format
```

后端验证（单元测试覆盖登录安全逻辑、自助注册约束、权限码校验、JWT 密钥与工单 ID 生成）：

```bash
cd backend
mvn -gs mvn-settings.xml test
```

生产构建生成在 `dist/`，该目录已被 Git 忽略，可部署到 Nginx 等静态服务器。

## 安全说明

- 生产环境必须覆盖默认数据库密码和 `NOVAOPS_JWT_SECRET`。
- 知识库文件、向量数据、本地环境变量和构建产物均不应提交到仓库。
- 业务数据归属由服务端认证上下文统一确定，前端无法自行声明。
