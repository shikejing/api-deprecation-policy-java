# API 版本管理与弃用下线能力

本服务在现有 Spring Boot 工程上补齐了接口版本管理与弃用下线能力：调用方可以按请求表达期望版本，维护方可以登记版本生命周期、在弃用窗口内灰度与回退、按计划时间强制下线，所有变更与越权尝试均被审计追溯。

## 1. 版本协商（Version Negotiation）

调用方通过以下任一方式表达期望版本，优先级从高到低：

1. **路径版本**：`GET /api/v2/greeting`（路径第二段，形如 `v1`、`v2`、`v-gray`）
2. **请求头版本**：`GET /api/greeting` 携带 `X-API-Version: v2`（请求头名可配置）
3. **默认版本**：两者都未提供时使用 `api.version.default-version`（默认 `v1`），行为确定

未显式声明版本时，响应头 `X-API-Version` 会回显实际解析到的版本。

```bash
# 路径协商
curl http://localhost:8080/api/v2/greeting

# 请求头协商
curl -H 'X-API-Version: v2' http://localhost:8080/api/greeting

# 未声明 -> 使用默认版本 v1
curl http://localhost:8080/api/greeting
```

## 2. 生命周期状态语义

每个版本有三种状态，任一版本的当前状态可通过管理接口随时查询：

| 状态 | 含义 | 调用行为 |
| --- | --- | --- |
| `AVAILABLE` | 可用 | 正常调用 |
| `DEPRECATED` | 已弃用 | 可调用，响应携带机器可解析的弃用时间与计划下线时间 |
| `RETIRED` | 已下线 | 调用被拒绝（410 `VERSION_RETIRED`） |

状态查询：

```bash
curl -u admin:admin-pass http://localhost:8080/admin/versions
curl -u admin:admin-pass http://localhost:8080/admin/versions/v1
```

## 3. 弃用提示（机器可解析）

版本处于弃用状态时，响应自动携带以下头，调用方可据此在过渡窗口内自行迁移：

- `Deprecation: @<epoch-seconds>` — 弃用时间（RFC 弃用头草案，机器可解析）
- `Sunset: <HTTP-date>` — 计划下线时间（RFC 8594，机器可解析）
- `X-API-Version: <version>` — 实际生效的版本

```bash
curl -i http://localhost:8080/api/greeting
# HTTP/1.1 200 OK
# X-API-Version: v1
# Deprecation: @1735689600
# Sunset: Sat, 01 Feb 2026 00:00:00 GMT
```

## 4. 计划下线强制拦截

- 弃用接口到达计划下线时间后，对该版本的调用被**强制拒绝**，返回 `410 Gone`，错误码 `VERSION_RETIRED`。
- 该拒绝原因与「版本不存在」明确区分：版本不存在返回 `404 Not Found` + `VERSION_NOT_FOUND`。
- 计划下线时间由定时任务自动扫描生效（间隔可配置），无需人工干预。

```bash
# 版本已下线
curl -i http://localhost:8080/api/v1/greeting
# HTTP/1.1 410 Gone
# {"error":"VERSION_RETIRED","message":"API version has been retired: v1","version":"v1", ...}

# 版本从未登记
curl -i http://localhost:8080/api/v9/greeting
# HTTP/1.1 404 Not Found
# {"error":"VERSION_NOT_FOUND","message":"Unknown API version: v9","version":"v9", ...}
```

## 5. 并发状态切换

- 状态对象为不可变记录，所有切换通过 `ConcurrentHashMap.compute` 原子完成，并发变更不会产生撕裂或中间态。
- 每次请求在进入时捕获状态快照并绑定到请求上下文，**状态切换对新请求立即生效，进行中的请求仍使用进入时的快照**，不会出现新旧混合的响应。

## 6. 灰度与回退

弃用支持按比例灰度（`grayPercent`，0–100）：按调用方身份（`X-Caller-Id`，缺失时回退为客户端地址）做稳定哈希分桶，同一调用方在灰度期间始终落在同一侧。

- `grayPercent=100`：所有调用方都看到弃用提示
- `grayPercent=50`：约一半调用方看到弃用提示，另一半不受影响（下线拦截仍按计划时间生效）
- `grayPercent=0`：所有调用方都看不到弃用提示（权威状态仍为 `DEPRECATED`）

回退（rollback）将版本恢复为 `AVAILABLE`：回退后任意一次查询、任意一个调用方得到的状态都一致，不会出现同一版本对不同请求返回矛盾状态。

```bash
# 50% 灰度弃用
curl -u admin:admin-pass -X POST http://localhost:8080/admin/versions/v2/deprecate \
  -H 'Content-Type: application/json' \
  -d '{"plannedRetirementTime":"2026-12-31T00:00:00Z","grayPercent":50}'

# 回退
curl -u admin:admin-pass -X POST http://localhost:8080/admin/versions/v2/rollback
```

## 7. 权限校验与审计

- 管理接口（`/admin/**`）使用 HTTP Basic 认证，`ADMIN` 角色可操作；`USER` 角色仅能访问业务接口。
- 未认证访问管理接口返回 `401`，已认证但权限不足返回 `403`。
- 所有管理操作（注册、弃用、回退、下线）以及被拒绝的访问尝试（401/403）均写入审计日志，可通过 `GET /admin/audit` 查询，包含操作人、动作、版本、时间、是否允许与详情。

```bash
curl -u admin:admin-pass http://localhost:8080/admin/audit
```

## 8. 管理接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/versions` | 登记新版本（`{"version":"v3"}`） |
| GET | `/admin/versions` | 查询全部版本状态 |
| GET | `/admin/versions/{version}` | 查询单版本状态 |
| POST | `/admin/versions/{version}/deprecate` | 弃用（可带 `deprecationTime`、`plannedRetirementTime`、`grayPercent`） |
| POST | `/admin/versions/{version}/rollback` | 回退为可用 |
| POST | `/admin/versions/{version}/retire` | 立即下线 |
| GET | `/admin/audit` | 查询审计记录 |

## 9. 配置项

所有配置均在 `api.version` 前缀下，可通过 `application.properties` 或环境变量覆盖：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `api.version.default-version` | `v1` | 调用方未声明版本时使用的默认版本 |
| `api.version.header-name` | `X-API-Version` | 版本协商请求头名 |
| `api.version.path-prefix` | `/api/` | 版本化业务接口的路径前缀 |
| `api.version.caller-header-name` | `X-Caller-Id` | 灰度分桶使用的调用方标识头 |
| `api.version.enforce-retirement` | `true` | 是否自动按计划下线时间拦截 |
| `api.version.retirement-check-interval-ms` | `1000` | 计划下线扫描间隔（毫秒） |
| `api.version.admin-username` | `admin` | 管理员账号 |
| `api.version.admin-password` | `admin-pass` | 管理员密码 |
| `api.version.admin-role` | `ADMIN` | 管理操作所需角色 |
| `api.version.operator-username` | `user` | 只读操作员账号 |
| `api.version.operator-password` | `user-pass` | 只读操作员密码 |
| `api.version.audit-max-entries` | `10000` | 审计日志内存保留条数 |

## 10. 自动化测试

测试覆盖以下场景（`mvn test`）：

- 版本协商：默认版本、请求头、路径、路径优先于请求头、未知版本 404、畸形版本 404
- 弃用提示：`Deprecation` / `Sunset` 机器可解析头
- 下线拦截：410 `VERSION_RETIRED` 与 404 `VERSION_NOT_FOUND` 的区分、计划下线时间自动生效
- 并发状态切换：多线程随机切换不产生撕裂状态；进行中请求不被切换影响
- 灰度与回退：灰度按调用方稳定分桶、回退后所有查询一致
- 权限与审计：401/403 拒绝、管理操作与越权尝试均可审计追溯
