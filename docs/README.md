# 文档索引

本目录是 AI 模拟面试 Agent 系统的文档中心。

## 当前有效文档（请以这些为准）

| 文档 | 内容 | 何时读 |
|---|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | **总体架构**：定位、Java/Python 职责、数据流、部署、演进路线、决策记录 | 新人入门 / 每次做架构决策前 |
| [`AGENT_DESIGN.md`](./AGENT_DESIGN.md) | **Agent 专题**：3 Agent 结构、题库三层、出题流程、评分体系、记忆、Prompt 约定 | 开发 Python 侧 / 设计算法前 |
| [`ENTITY_PLAN.md`](./ENTITY_PLAN.md) | **Java Entity/Repository 设计清单（B-0）**：Phase 1 要建哪些 Entity、字段映射、JPA 策略、B-1 落地顺序 | 开始写/改 Entity 前必读 |
| [`PHASE0_GUIDE.md`](./PHASE0_GUIDE.md) | **Phase 0 学习笔记**：WebSocket 鉴权（HandshakeInterceptor）原理 + 配置项安全化（环境变量/docker-compose）详解 | 学习 WS 鉴权 / 配置安全 |
| [`../backend/src/main/resources/db/migration/V1__init_schema.sql`](../backend/src/main/resources/db/migration/V1__init_schema.sql) | **数据模型**：13 张表完整定义，字段注释详尽 | 写 Entity / SQL 前 |

## 历史文档（已归档）

v0 阶段遗留文档已统一归档到：
- `docs/legacy/`（项目结构、文件清单、快速开始等 6 份）
- `backend/docs/legacy/`（旧测试报告）

归档理由与处置建议见 [`legacy/README.md`](./legacy/README.md)。

## 文档维护约定

1. **文档与代码的任何偏差视为 bug**。改代码时必须同步改文档，否则不合并。
2. **架构决策**记录在 `ARCHITECTURE.md` §11（ADR）中，格式：
   ```
   ### ADR-XXX：<一句话决策>
   - 决策：...
   - 理由：...
   - 代价：...
   - 备选方案：...
   ```
3. **Schema 变更**：
   - 通过新的 Flyway migration（`V{n}__xxx.sql`）
   - 同步更新 `ARCHITECTURE.md` §4 和 `AGENT_DESIGN.md` 中受影响段落
4. 新增 Agent / 工作流：在 `AGENT_DESIGN.md` 对应章节补充，不要开新文档分散信息。

## 当前项目阶段

**Phase 0 — 基础重构中**

- [x] 推翻旧 Schema，落地 `V1__init_schema.sql` 新版
- [x] 产出本架构文档与 Agent 设计文档
- [ ] 清理旧代码：删除 `org_id`、重构现有 3 个 Entity 到新主键类型
- [ ] 修复 WebSocket 鉴权
- [ ] Python 项目骨架初始化

进入 Phase 1（文本 MVP 闭环）前 Phase 0 全部完成。
