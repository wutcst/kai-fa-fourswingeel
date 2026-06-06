#!/bin/bash
echo "====================================="
echo "  Slay the Spire 后端启动脚本 (Linux/macOS)"
echo "  要求: 已安装 Maven (mvn 命令可用)"
echo "====================================="
echo ""
cd backend
echo "[1/2] 编译并启动后端..."
echo ""
mvn spring-boot:run
