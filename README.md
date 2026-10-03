# 接口版本管理与弃用下线策略

本服务在现有 Spring Boot 工程上提供统一的接口版本协商、生命周期管理、
弃用提示、下线拦截、权限校验与审计能力。

## 版本协商策略

每个请求按以下优先级确定期望版本（行为确定，无歧义）：

1. **请求头**：`X-Api-Version: v2`（头名称可配置）；
2. **路径段**：`/api/v2/echo` 中的 `v2`（可通过配置关闭）；
3. **默认版本**：`api.versioning.default-version`（默认 `v1`）。

协商成功的版本通过响应头 `X-Api-Version-Served` 回显。
请求不存在的版本返回 `404` + `{"code":"VERSION_UNKNOWN"}`。

## 生命周期状态语义

| 状态 | 语义 | 调用表现 |
|------|------|----------|
| `AVAILABLE` | 可用 | 正常响应，无附加头 |
| `DEPRECATED` | 已弃用（过渡窗口内仍可用） | 正常响应，携带 `Deprecation` 与 `Sunset` 头 |
| `SUNSET` | 已下线 | `410 Gone` + `{"code":"VERSION_SUNSET"}` |

- **弃用提示**（机器可解析）：
  - `Deprecation: @<unix 时间戳>`（RFC 9745）——弃用发生时间；
  - `Sunset: <HTTP-date>`（RFC 8594）——计划下线时间。
  调用方可据此在过渡窗口内自行迁移。
- **强制拦截**：超过计划下线时间后，即使状态尚未被人工置为 `SUNSET`，
  该版本的调用也会被拒绝（`410 VERSION_SUNSET`），与版本不存在
  （`404 VERSION_UNKNOWN`）明确区分。
- **并发语义**：每个版本的状态是不可变快照，切换原子生效——新请求立即看到
  新状态；进行中的请求在本次请求内始终使用同一份快照，不会出现新旧混合的响应。
- **灰度与回退**：弃用可指定 `rolloutPercent`（0-100），按调用方身份
  （`X-Caller-Id`，缺省为来源 IP）哈希分桶，同一调用方观察到的弃用语义稳定；
  `rollback` 可在过渡窗口内撤销弃用，回退后任意查询得到的状态一致
  （已下线的版本不可回退）。

## 管理端点（/admin）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/versions` | 查询全部版本状态 | 开放 |
| GET | `/admin/versions/{v}` | 查询单个版本状态 | 开放 |
| POST | `/admin/versions/{v}/register` | 登记版本 | 需要令牌 |
| POST | `/admin/versions/{v}/deprecate?sunsetAt=...&rolloutPercent=...` | 弃用（含灰度） | 需要令牌 |
| POST | `/admin/versions/{v}/sunset` | 立即下线 | 需要令牌 |
| POST | `/admin/versions/{v}/rollback` | 回退为可用 | 需要令牌 |
| GET | `/admin/audit` | 查询审计记录 | 需要令牌 |

变更类操作必须携带请求头 `X-Admin-Token`；越权请求返回
`403 {"code":"FORBIDDEN"}` 并写入审计记录（`success=false`），可追溯。
所有变更操作均记录审计：时间、操作者、动作、目标版本、结果与明细。

## 配置项（application.properties）

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `api.versioning.default-version` | `v1` | 未声明版本时的默认版本 |
| `api.versioning.header-name` | `X-Api-Version` | 版本协商请求头 |
| `api.versioning.path-negotiation-enabled` | `true` | 是否允许路径段协商 |
| `api.versioning.admin-token` | `change-me` | 管理端点令牌（生产必须修改） |

## 测试

`mvn test` 覆盖：版本协商（头/路径/默认/未知版本）、弃用提示头、
下线拦截与错误码区分、计划下线时间强制拦截、并发状态切换无混合响应、
灰度稳定性与回退一致性、越权拒绝与审计留痕。
