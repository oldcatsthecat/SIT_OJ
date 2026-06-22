# SIT OJ 后端接口文档

## 模块概览

| 模块 | Controller | 基础路径 |
|------|-----------|---------|
| problem | ProblemController | `/problems` |
| problem | AdminProblemController | `/admin/problems` |
| user | UserController | `/users` |
| user | AdminUserController | `/admin/users` |
| submission | SubmissionController | `/submissions` |
| competition | CompetitionController | `/competitions` |
| competition | AdminCompetitionController | `/admin/competitions` |
| competition | ParticipationController | `/competitions` |
| judge | JudgeController | `/judge` |
| judge | AdminJudgeController | `/admin/judge` |

---

## 1. ProblemController — `/problems`

### `GET /problems/list`
获取分页题目列表（用户端），可按 problemId 筛选。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| current | Integer | 否 | 1 | 当前页码 |
| size | Integer | 否 | 20 | 每页条数 |
| problemId | Integer | 否 | — | 按题目ID筛选 |

返回: `Result<IPage<Problem>>`

### `GET /problems/{id}`
获取单个题目详情。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 题目ID |

返回: `Problem`

### `PUT /problems/inner/update-stats`
内部接口：更新题目统计（通过数/提交数）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| problemId | Integer | 是 | 题目ID |
| isAccepted | boolean | 是 | 是否通过 |

返回: `void`

---

## 2. AdminProblemController — `/admin/problems`

### `POST /admin/problems/save`
新增或更新题目。有 ID 则更新，无 ID 则新增。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| problem | Problem (Body) | 是 | 题目实体 |

返回: `Result<Void>`

### `DELETE /admin/problems/{id}`
删除题目。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 题目ID |

返回: `Result<Void>`

### `GET /admin/problems/{id}`
管理员获取题目详情（编辑用）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 题目ID |

返回: `Result<Problem>`

### `POST /admin/problems/testcase/upload`
上传题目的测试数据文件。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 测试数据文件 |
| problemId | String | 是 | 题目ID |

返回: `Result`

---

## 3. UserController — `/users`

### `POST /users/register`
用户注册。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| params | Map (Body) | 是 | 含 username, password, email 等 |

返回: `Result<Void>`

### `POST /users/login`
用户登录，返回 JWT token。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| loginRequest | Map (Body) | 是 | 含 username, password |

返回: `Result<String>` (JWT token)

### `GET /users/inner/{id}`
内部接口：Feign 获取用户信息（不含密码）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 用户ID |

返回: `User`

### `GET /users/me`
获取当前登录用户信息。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | String (Header) | 是 | JWT token |

返回: `Result<User>`

### `PUT /users/update`
更新当前用户资料（邮箱、密码、真实姓名等）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| params | Map (Body) | 是 | 待更新字段 |
| request | HttpServletRequest | — | 获取当前用户身份 |

返回: `Result<Void>`

### `POST /users/sendCode`
发送邮箱验证码。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| email | String | 是 | — | 邮箱地址 |
| type | String | 否 | register | register 或 reset |

返回: `Result<Void>`

### `POST /users/resetPassword`
通过验证码重置密码。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| params | Map (Body) | 是 | 含 email, code, newPassword |

返回: `Result<Void>`

---

## 4. AdminUserController — `/admin/users`

### `GET /admin/users/list`
分页获取所有用户列表。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| current | Integer | 否 | 1 | 当前页码 |
| size | Integer | 否 | 20 | 每页条数 |
| Authorization | String (Header) | 是 | 管理员 JWT |

返回: `Result<IPage<User>>`

### `DELETE /admin/users/{id}`
删除用户（含防自删保护）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 用户ID |
| Authorization | String (Header) | 是 | 管理员 JWT |

返回: `Result<Void>`

### `PUT /admin/users/update`
管理员修改用户信息（如角色变更）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| user | User (Body) | 是 | 用户实体 |
| Authorization | String (Header) | 是 | 管理员 JWT |

返回: `Result<Void>`

---

## 5. SubmissionController — `/submissions`

### `POST /submissions/submit`
提交代码进行判题。若请求中缺 userId，从 JWT 中提取。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| submission | Submission (Body) | 是 | 含 problemId, code, language 等 |
| request | HttpServletRequest | — | 获取用户身份 |

返回: `Result`

### `GET /submissions/{id}`
获取提交详情（含权限校验）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 提交ID |

返回: `Result`

### `GET /submissions/inner/{id}`
内部接口：获取提交原始实体（无鉴权）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 提交ID |

返回: `Submission`

### `GET /submissions/list`
用户端分页获取自己的提交列表，可按题目筛选。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| current | Integer | 否 | 1 | 当前页码 |
| size | Integer | 否 | 10 | 每页条数 |
| problemId | Integer | 否 | — | 按题目筛选 |
| userId | Integer (Attr) | 是 | — | 从 JWT 注入 |
| userRole | String (Attr) | 是 | — | 从 JWT 注入 |

返回: `Result`

### `GET /submissions/inner/stats/{competitionId}`
内部接口：获取某比赛的统计数据。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| competitionId | Integer (Path) | 是 | 比赛ID |

返回: `Result`

### `GET /submissions/competition/list`
分页获取某比赛的提交列表。封榜期间对非管理员/非本人遮罩状态。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| current | Integer | 否 | 1 | 当前页码 |
| size | Integer | 否 | 15 | 每页条数 |
| competitionId | Integer | 是 | — | 比赛ID |
| request | HttpServletRequest | — | 获取用户身份 |

返回: `Result`

### `GET /submissions/inner/export/{competitionId}`
内部接口：导出一场比赛的全部提交数据（供 ICPC Resolver 使用）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| competitionId | Integer (Path) | 是 | 比赛ID |

返回: `List<Map<String, Object>>`

---

## 6. CompetitionController — `/competitions`

### `GET /competitions/list`
分页获取比赛列表，附带当前用户的报名状态。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| current | Integer | 否 | 1 | 当前页码 |
| size | Integer | 否 | 20 | 每页条数 |

返回: `Result`

### `GET /competitions/{id}`
获取比赛详情（含题目列表、是否报名、题目 AC 状态）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 比赛ID |
| userId | Integer (Attr) | 否 | 从 JWT 注入，用于判断报名状态和已 AC 题目 |

返回: `Result`

### `GET /competitions/{id}/rank`
获取比赛排行榜（ACM 模式：解题数降序，罚时升序）。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| id | Integer (Path) | 是 | 比赛ID |
| current | Integer | 否 | 1 | 当前页码 |
| size | Integer | 否 | 20 | 每页条数 |

返回: `Result` — `{records, total, isFrozen, frozenAttempts}`

### `POST /competitions/{id}/submit`
在比赛中提交代码。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 比赛ID |
| submitData | Map (Body) | 是 | 含 problemId, code, language |
| request | HttpServletRequest | — | 获取用户身份 |

返回: `Result`

### `GET /competitions/{id}/stats`
获取比赛各题目的统计数据。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 比赛ID |

返回: `Result`

### `POST /competitions/internal/updateStats`
内部接口：判题完成后回调，更新 ACM 排名统计。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Integer | 是 | 用户ID |
| competitionId | Integer | 是 | 比赛ID |
| problemId | Integer | 是 | 题目ID |
| status | String | 是 | 判题状态 (AC/WA/TLE/CE...) |
| submissionTime | String | 是 | 提交时间（用于封榜判断） |

返回: `Result`

### `GET /competitions/inner/{id}/frozen`
内部接口：检查比赛是否处于封榜期。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 比赛ID |

返回: `Result<Boolean>`

---

## 7. AdminCompetitionController — `/admin/competitions`

### `POST /admin/competitions/create`
创建新比赛。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| competition | Competition (Body) | 是 | 比赛实体 |

返回: `Result`

### `POST /admin/competitions/problems/add`
为比赛批量添加题目。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| data | Map (Body) | 是 | 含 competitionId, problemIds |

返回: `Result`

### `PUT /admin/competitions/update`
更新比赛信息（仅允许修改名称，开始/结束/封榜时间创建后不可改）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| competition | Competition (Body) | 是 | 比赛实体 |

返回: `Result`

### `DELETE /admin/competitions/delete/{id}`
删除比赛。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 比赛ID |

返回: `Result`

### `POST /admin/competitions/{id}/unfreeze`
管理员手动解封比赛。重建全部 MySQL 统计和 Redis 排名。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 比赛ID |

返回: `Result`

### `GET /admin/competitions/{id}/export`
导出比赛数据为 ICPC Resolver 兼容的 NDJSON 事件流。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Integer (Path) | 是 | 比赛ID |

返回: `Result<String>` (NDJSON 内容)

---

## 8. ParticipationController — `/competitions`

### `POST /competitions/{cid}/register`
报名参加比赛。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cid | Integer (Path) | 是 | 比赛ID |
| userId | Integer (Attr) | 是 | 从 JWT 注入 |

返回: `Result<String>`

### `GET /competitions/{cid}/status`
检查当前用户是否已报名该比赛。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cid | Integer (Path) | 是 | 比赛ID |
| userId | Integer (Attr) | 是 | 从 JWT 注入 |

返回: `Result<Boolean>`

---

## 9. JudgeController — `/judge`

### `POST /judge/doJudge`
执行完整判题流程（拉取题目数据 → 调用判题机 → 解析结果）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| submissionId | Integer | 是 | 提交ID |

返回: `JudgeResultResponse`

---

## 10. AdminJudgeController — `/admin/judge`

### `POST /admin/judge/judge_server_heartbeat`
判题机心跳上报（CPU、内存、最后活跃时间）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| payload | Map (Body) | 是 | 含 cpu, memory, status 等 |

返回: `Map<String, Object>`

### `GET /admin/judge/server_status`
获取判题机最新状态。

| 参数 | — |
|------|------|

返回: `Map<String, Object>` — `{cpu, memory, lastSeen, status}`
