# OpenIOT 家庭设备控制台

家庭局域网内的 IoT 平台 MVP：设备通过 MQTT 上报状态，平台记录数据、下发控制命令，并按回执显示真实执行结果。

界面上的设备、命令和告警全部来自后端数据库，没有内置演示数据。数据库为空时页面就是空的，接入真实设备或启动模拟器后才会出现内容。

## 架构

```
浏览器(Vue 3)  --HTTP/JSON-->  平台服务(Spring Boot)  --JDBC-->  PostgreSQL
                                      |
                                      | MQTT
                                      v
                                Mosquitto Broker
                                      ^
                                      |
                              真实设备 / 设备模拟器
```

平台与设备不直连，全部通过 Broker 按主题交换消息。平台订阅设备上报，设备订阅自己的命令主题。

## 目录结构

```
src/            前端（Vue 3 + TypeScript + Vite）
backend/        后端（Java 17 + Spring Boot 3.3）
deploy/         容器相关配置（Mosquitto）
docker-compose.yml   Broker 与数据库编排
tests/          前端单元测试
```

## 前置条件

- Docker Desktop（运行 Broker 与数据库）
- JDK 17、Maven 3.9
- Node.js 20 以上

## 快速开始

**1. 创建本地配置**

本项目不提交真实密码、管理员账号或家庭网络地址。

```powershell
Copy-Item .env.example .env
Copy-Item backend/config/application-local.example.yml backend/config/application-local.yml
```

在 `.env` 中设置 PostgreSQL 密码，在 `application-local.yml` 中设置相同的数据源密码、管理员账号以及可选的电视地址。两个文件都已被 `.gitignore` 忽略。

**2. 启动 Broker 与数据库**

```
docker compose up -d
```

**3. 启动后端（启用 local profile）**

```
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=--spring.config.additional-location=optional:file:./config/ --spring.profiles.active=local"
```

看到 `Started OpenIotPlatformApplication` 并且日志里出现「已连接 MQTT Broker」即成功。

**4. 启动前端**

```
npm install
npm run dev
```

浏览器打开 http://localhost:5173/ ，使用 `application-local.yml` 中配置的管理员账号登录。

**5.（可选）启动设备模拟器**

没有硬件时，用它验证完整链路。它会像真实设备一样连 Broker、按主题上报、订阅命令并回执：

```
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=--spring.config.additional-location=optional:file:./config/ --spring.profiles.active=local --openiot.simulator.enabled=true"
```

在界面上创建产品和设备后，模拟器会自动接管这些设备并开始上报数据。

## 设备接入契约

设备编号（`code`）在平台创建时指定，同时是 MQTT 主题里的标识。

| 方向      | 主题                           | 说明                         |
| ------- | ---------------------------- | -------------------------- |
| 设备 → 平台 | `openiot/{设备编号}/telemetry`   | 上报状态与遥测                    |
| 设备 → 平台 | `openiot/{设备编号}/command/ack` | 回报命令执行结果                   |
| 设备 → 平台 | `openiot/{设备编号}/status`      | 上下线，建议用遗嘱消息(LWT)并设置 retain |
| 平台 → 设备 | `openiot/{设备编号}/command`     | 下发目标状态，QoS 1               |

上报遥测：

```json
{"type":"telemetry","deviceCode":"OI-240018","ts":"2026-10-16T12:00:00Z","state":{"power":true,"temperature":24.8}}
```

上报上线下线（推荐作为 LWT）：

```json
{"type":"status","deviceCode":"OI-240018","online":false}
```

接收命令：

```json
{"type":"command","commandId":"8f2a...","deviceCode":"OI-240018","ts":"2026-10-16T12:00:00Z","target":{"power":true}}
```

回报执行结果：

```json
{"type":"commandAck","deviceCode":"OI-240018","commandId":"8f2a...","status":"confirmed","message":"设备已执行","state":{"power":true}}
```

`status` 取 `confirmed` 或 `rejected`。设备端应按 `commandId` 做幂等处理，重复投递不能造成状态反转。

### 关于确认能力

平台按产品分类区分两类设备，这一点直接决定界面怎么显示：

- 能回报实际状态的设备（例如智能继电器）：收到 `commandAck` 且状态为 `confirmed` 后，才更新界面上的实际状态。
- 只能证明「已发出」的设备（产品分类为「红外网关」）：命令状态停留在「已发送，未确认」，即使网关回了 ack 也不会改写家电实际状态。

设备离线时平台不会下发命令，直接记录为「不可达」。命令下发后超过 `command-timeout-seconds` 未收到回执，记为「结果未知」，而不是判定失败。

## 后端接口

除登录外都需要 `Authorization: Bearer <token>`。写操作统一返回最新的平台状态快照。

| 方法                  | 路径                                      | 说明                                      |
| ------------------- | --------------------------------------- | --------------------------------------- |
| POST                | `/api/auth/login`                       | 登录，返回令牌                                 |
| POST                | `/api/auth/logout`                      | 退出                                      |
| GET                 | `/api/state`                            | 平台状态快照（前端轮询这个接口）                        |
| POST / PUT / DELETE | `/api/products`、`/api/products/{id}`    | 产品增改删                                   |
| POST                | `/api/devices`                          | 创建设备                                    |
| PATCH               | `/api/devices/{id}/enabled`             | 启用/停用设备                                 |
| POST                | `/api/devices/{id}/commands`            | 下发命令，body 为 `{"target":{"power":true}}` |
| POST                | `/api/alarms/{id}/acknowledge`、`/close` | 确认/关闭告警                                 |
| POST / PATCH        | `/api/rules`、`/api/rules/{id}/enabled`  | 保存规则、启停规则                               |
| PUT                 | `/api/settings/mqtt`                    | 修改 Broker 连接参数并重连                       |
| POST                | `/api/settings/mqtt/reconnect`          | 按当前参数重连                                 |

## 配置

### 前端

复制 `.env.example` 为 `.env` 后按需修改。`.env` 同时供 Docker Compose 和前端开发服务器使用：

| 变量                   | 默认值                     | 说明                       |
| -------------------- | ----------------------- | ------------------------ |
| `VITE_API_BASE`      | `http://localhost:8080` | 后端地址，改完需重启 `npm run dev` |
| `POSTGRES_DB`        | `openiot`               | PostgreSQL 数据库名          |
| `POSTGRES_USER`      | `openiot`               | PostgreSQL 用户名            |
| `POSTGRES_PASSWORD`  | 无                       | PostgreSQL 密码，必须填写        |

### 后端

`application.yml` 只保存非敏感默认值和环境变量占位符。真实密码、管理员账号和电视地址写入 `backend/config/application-local.yml`，该文件不会被 Git 跟踪，也不会被打进 jar。

| 配置项                                        | 默认值                                        | 说明                    |
| ------------------------------------------ | ------------------------------------------ | --------------------- |
| `openiot.mqtt.broker-url`                  | `tcp://localhost:1883`                     | Broker 地址             |
| `openiot.mqtt.username` / `password`       | 空                                          | Broker 开启认证后填写        |
| `openiot.device.offline-threshold-seconds` | 90                                         | 超过该时长没有上报即判定离线        |
| `openiot.device.command-timeout-seconds`   | 15                                         | 命令等待回执的超时时间           |
| `openiot.security.admin-email`             | 无                                          | 本地配置中填写登录账号           |
| `openiot.security.admin-password`          | 无                                          | 本地配置中填写登录密码           |
| `openiot.simulator.enabled`                | `false`                                    | 是否启用开发用设备模拟器          |
| `openiot.tv.enabled`                       | `false`                                    | 是否启用小米电视局域网适配器        |
| `openiot.tv.ip` / `port`                   | `127.0.0.1` / `6095`                       | 本地配置中填写电视地址           |
| `openiot.tv.code`                          | 空                                          | 本地配置中填写电视设备编号         |
| `openiot.tv.poll-interval-millis`          | `15000`                                    | 电视探活间隔                |
| `spring.datasource.url`                    | `jdbc:postgresql://localhost:5432/openiot` | 数据库地址                 |

覆盖方式有两种，任选其一。

命令行参数示例：

```
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=--spring.config.additional-location=optional:file:./config/ --spring.profiles.active=local --openiot.mqtt.broker-url=tcp://broker.local:1883"
```

环境变量（适合容器部署）。键名规则是点换成下划线、去掉连字符、全大写，例如 `openiot.mqtt.broker-url` 对应 `OPENIOT_MQTT_BROKER_URL`。示例见 `backend/.env.example`。

## 测试

```
npm test                       # 前端单元测试
npm run typecheck              # 前端类型检查
mvn -f backend/pom.xml test    # 后端集成测试
```

后端集成测试会连接 `localhost:1883` 的真实 Broker，覆盖遥测入库、命令确认、红外网关不误报、离线拒发命令四条链路。未启动 Broker 时这些用例会自动跳过，请先执行 `docker compose up -d`。

## 安全说明

当前 `deploy/mosquitto/mosquitto.conf` 允许匿名连接，这是为了本机开发方便。**接入真实设备前必须做三件事**：

1. Broker 开启账号密码认证，并为每台设备分配独立凭据。
2. 按设备身份限制可发布/订阅的主题，设备只能访问自己的主题。
3. 远程访问必须走 TLS，不要把 Broker 或管理接口直接暴露到公网。

登录目前是本地配置中的单一管理员账号，令牌存在后端内存中，后端重启后需要重新登录。正式使用前应替换为真实用户体系。

## 当前限制

- 页面用 3 秒轮询刷新，尚未使用 WebSocket。
- 设备凭据只在界面上展示，还没有轮换与吊销流程。
- 历史数据只保留每台设备最近 240 条。
- 没有家庭/租户概念，所有设备属于同一套部署。
- 小米电视通过 6095 端口的局域网 HTTP 接口接入，只能确认指令已送达，读不到真实开关与音量状态；电视 IP 由路由器 DHCP 分配，变化后需要修改配置并重启后端。
