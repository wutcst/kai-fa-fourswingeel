#!/bin/bash
set -e

echo "=== 杀戮尖塔 一键启动 ==="

# 1. 编译前端
echo "[1/3] 安装前端依赖并编译..."
cd frontend
npm install --silent
npm run build --silent
cd ..

# 2. 拷贝资源
echo "[2/3] 将前端资源复制到后端静态目录..."
mkdir -p backend/src/main/resources/static
cp -r frontend/build/* backend/src/main/resources/static/

# 3. 启动后端
echo "[3/3] 启动后端服务..."
cd backend
mvn clean package -DskipTests -q
echo "服务已启动，请在浏览器中打开 http://localhost:8080"
java -jar target/slay-the-spire-clone-*.jar
