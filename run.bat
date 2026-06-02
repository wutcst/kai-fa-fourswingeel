@echo off
echo === 杀戮尖塔 一键启动 ===

echo [1/3] 安装前端依赖并编译...
cd frontend
call npm install --silent
call npm run build --silent
cd ..

echo [2/3] 将前端资源复制到后端静态目录...
if not exist backend\src\main\resources\static mkdir backend\src\main\resources\static
xcopy /E /Y frontend\build\* backend\src\main\resources\static\

echo [3/3] 编译并启动后端...
cd backend
call mvn clean package -DskipTests -q
echo 服务已启动，请在浏览器中打开 http://localhost:8080
for %%f in (target\slay-the-spire-clone-*.jar) do set JAR=%%f
java -jar %JAR%
