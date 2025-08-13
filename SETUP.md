# AI Misaki Mod - 配置指南

## 环境变量设置

### Windows 系统
1. 打开"系统属性" → "高级" → "环境变量"
2. 在"用户变量"或"系统变量"中点击"新建"
3. 变量名：`DEEPSEEK_API_KEY`
4. 变量值：你的DeepSeek API密钥
5. 确定保存并重启命令行

### macOS/Linux 系统
在 `~/.bashrc` 或 `~/.zshrc` 文件中添加：
```bash
export DEEPSEEK_API_KEY="your_api_key_here"
```
然后执行 `source ~/.bashrc` 或重启终端。

### 通过启动脚本设置
创建启动脚本：

**Windows (start.bat):**
```batch
@echo off
set DEEPSEEK_API_KEY=your_api_key_here
java -jar fabric-server-mc.1.21.1-loader.0.16.14-launcher.1.0.1.jar nogui
pause
```

**Linux/macOS (start.sh):**
```bash
#!/bin/bash
export DEEPSEEK_API_KEY="your_api_key_here"
java -jar fabric-server-mc.1.21.1-loader.0.16.14-launcher.1.0.1.jar nogui
```

## DeepSeek API 密钥获取

1. 访问 [DeepSeek官网](https://platform.deepseek.com/)
2. 注册账号并登录
3. 进入API管理页面
4. 创建新的API密钥
5. 复制密钥并配置到环境变量中

## 模组配置文件

模组会在 `config/ai-misaki-mod.json` 创建配置文件：

```json
{
  "ai_settings": {
    "model": "deepseek-chat",
    "max_tokens": 1000,
    "temperature": 0.7
  },
  "auto_messages": {
    "enabled": true,
    "interval_minutes": 5,
    "tip_interval_minutes": 30
  },
  "chat_settings": {
    "response_delay_ms": 1000,
    "max_context_length": 150
  },
  "teleportation": {
    "predefined_locations": {
      "spawn": [0, 70, 0],
      "city": [100, 70, 100],
      "mine": [0, 20, 0],
      "farm": [-100, 70, -100],
      "beach": [0, 70, 500],
      "mountain": [200, 120, 200]
    }
  },
  "permissions": {
    "allow_item_giving": true,
    "allow_teleportation": true,
    "allow_weather_control": true,
    "allow_time_control": true,
    "allow_healing": true
  }
}
```

## 性能优化配置

### JVM参数建议
```bash
java -Xmx4G -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -jar server.jar
```

### 网络配置
确保服务器可以访问以下域名：
- `api.deepseek.com`
- `platform.deepseek.com`

### 防火墙设置
如果使用防火墙，需要允许HTTPS出站连接（端口443）。

## 故障排除

### 常见问题

1. **AI不响应**
   - 检查API密钥是否正确设置
   - 确认网络连接正常
   - 查看服务器日志是否有错误信息

2. **功能权限错误**
   - 确认玩家有足够的权限
   - 检查配置文件中的权限设置

3. **传送失败**
   - 确认目标坐标安全
   - 检查目标世界是否存在

4. **物品给予失败**
   - 确认物品ID正确
   - 检查玩家背包是否有空间

### 日志文件位置
- 服务端：`logs/latest.log`
- 客户端：`.minecraft/logs/latest.log`

### 调试模式
在配置文件中启用调试模式：
```json
{
  "debug": {
    "enabled": true,
    "log_ai_requests": true,
    "log_tool_calls": true
  }
}
```

## 服务器部署

### Docker 部署
```dockerfile
FROM openjdk:21-jre-slim

WORKDIR /minecraft
COPY server.jar .
COPY mods/ mods/
COPY config/ config/

ENV DEEPSEEK_API_KEY=your_api_key_here

EXPOSE 25565

CMD ["java", "-jar", "server.jar", "nogui"]
```

### 自动重启脚本
```bash
#!/bin/bash
while true; do
    echo "Starting Minecraft server..."
    java -jar server.jar nogui
    echo "Server stopped. Restarting in 10 seconds..."
    sleep 10
done
```

## 更新升级

### 自动更新检查
模组会自动检查更新并在控制台显示通知。

### 手动更新步骤
1. 下载新版本模组文件
2. 停止服务器
3. 替换 `mods/` 文件夹中的模组文件
4. 重启服务器

### 配置文件迁移
新版本会自动迁移旧配置文件，无需手动操作。

---

需要更多帮助？请查看 [GitHub Issues](https://github.com/Misaki030112/mc-ai-mod/issues)。