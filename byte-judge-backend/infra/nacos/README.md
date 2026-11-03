# 本地 Nacos

Standalone + Derby；配置保存在 Compose 命名卷。服务 API 为 127.0.0.1:8848，客户端 gRPC 为 9848，控制台为 http://127.0.0.1:8088/。

本地隔离环境关闭鉴权，所有端口绑定回环。此配置仅适用于宿主机启动 Java 服务；部署到其他机器需重新配置鉴权、网络与实例地址。

3.3.0 镜像启动脚本仍要求 token 和 identity 环境变量，由 init-local.ps1 生成。冒烟脚本使用 [v3 客户端 API](https://nacos.io/en/docs/v3.0/manual/user/open-api/) 查询实例；旧 v1 API 在目标镜像不可用。控制台 / 管理 API 的身份校验独立于客户端开关，浏览控制台需按提示初始化管理员。

本轮接入注册发现；配置中心暂不接入，不需要导入 Data ID。后续外置配置使用 spring.config.import，启动地址和凭据保留在环境中。
