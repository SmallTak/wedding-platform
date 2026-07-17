# Deploy

第一阶段部署采用 Nginx、Spring Boot 和 MySQL，图片文件保存在服务器本地磁盘。

一个域名即可承载全部服务：

- `/`：公开官网。
- `/console/`：管理员与创作者工作台。
- `/api/`：Spring Boot API。

管理端生产构建命令：

```bash
VITE_BASE_PATH=/console/ npm run build
```

生产环境启动后端前至少需要设置：

```text
SPRING_PROFILES_ACTIVE=prod
DB_HOST=127.0.0.1
DB_NAME=wedding_platform
DB_USERNAME=wedding_app
DB_PASSWORD=replace-me
JWT_SECRET=replace-with-at-least-32-random-bytes
STORAGE_ROOT=/home/apps/wedding-platform/storage
```

首次部署可以临时设置 `BOOTSTRAP_ADMIN_ENABLED=true`、`BOOTSTRAP_ADMIN_MOBILE` 和
`BOOTSTRAP_ADMIN_PASSWORD` 创建首个管理员，创建成功后关闭该开关。

## iot 服务器约定

- SSH 目标：`iot`。
- 应用目录：`/home/apps/wedding-platform`。
- 后端端口：`8080`，只由 Nginx 反向代理。
- systemd 服务：`wedding-platform.service`。
- Nginx：`/usr/local/nginx/sbin/nginx`。
- Nginx 站点配置：`/usr/local/nginx/conf/conf.d/photo.shop-hz.top.conf`。
- 证书目录：`/usr/local/nginx/conf/ssl/photo.shop-hz.top`。
- 域名：`photo.shop-hz.top`，`www.photo.shop-hz.top` 跳转到主域名。
- MySQL：复用参考项目所在实例，独立使用 `wedding_platform` schema 和 `wedding_app` 用户。

iot 服务器只有 JDK 17，因此项目以 Java 17 为生产目标编译。

阿里云 DNS 当前已配置主域名：

```text
photo.shop-hz.top      A      47.104.21.68
```

`www.photo.shop-hz.top` 证书已覆盖，但只有在需要该别名时才需要额外添加同 IP 的 A 记录。

## 证书

证书和私钥保留在工作站，不提交 Git。默认读取：

```text
~/Downloads/26141245_photo.shop-hz.top_nginx/photo.shop-hz.top.pem
~/Downloads/26141245_photo.shop-hz.top_nginx/photo.shop-hz.top.key
```

如果原始文件不存在，脚本会回退读取下载目录下的 `26141245_photo.shop-hz.top_nginx.zip`。
部署前会检查有效期、域名和公私钥是否匹配。远端私钥权限固定为 `0600`。

只校验证书和部署输入，不修改服务器：

```bash
DRY_RUN=1 ./deploy/scripts/deploy-nginx.sh
```

只安装证书和 Nginx 配置：

```bash
./deploy/scripts/deploy-nginx.sh
```

脚本会备份现有配置，执行 Nginx 配置测试；测试失败时自动恢复，不会 reload 错误配置。

## 发布命令

首次部署前，在服务器创建 `/etc/wedding-platform/wedding-platform.env`，可参考
`deploy/wedding-platform.env.example`。确认数据库和环境变量完成后执行：

```bash
./deploy/scripts/deploy-all.sh
```

也可以分开执行：

```bash
./deploy/scripts/build-all.sh
./deploy/scripts/deploy-app.sh
./deploy/scripts/deploy-nginx.sh
```

部署完成后的检查：

```bash
ssh iot 'systemctl status wedding-platform --no-pager'
ssh iot '/usr/local/nginx/sbin/nginx -t'
curl -I http://photo.shop-hz.top/
curl -I https://photo.shop-hz.top/
curl https://photo.shop-hz.top/api/public/status
```

截至 2026-07-17，生产服务、HTTPS、主域名解析和登录接口均已验证。首个生产管理员已经创建，
`BOOTSTRAP_ADMIN_ENABLED` 已恢复为 `false`，环境文件中不保留临时密码。
