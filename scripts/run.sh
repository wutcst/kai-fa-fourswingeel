#!/bin/bash
set -e

echo "=== 杀戮尖塔 一键启动 ==="

# 编译前端
echo "[1/3] 编译前端..."
cd frontend
npm install --silent
npm run build --silent
cd ..

# 复制前端产物到后端静态资源目录
echo "[2/3] 拷贝前端产物..."
mkdir -p backend/src/main/resources/static
cp -r frontend/build/* backend/src/main/resources/static/

# 编译后端并启动
echo "[3/3] 编译后端..."
cd backend
mvn clean package -DskipTests -q
echo "启动服务..."
java -jar target/slay-the-spire-clone-*.jar
