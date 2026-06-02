@echo off
echo === 杀戮尖塔 一键启动 ===

echo [1/3] 编译前端...
cd frontend
call npm install --silent
call npm run build --silent
cd ..

echo [2/3] 拷贝前端产物...
if not exist backend\src\main\resources\static mkdir backend\src\main\resources\static
xcopy /E /Y frontend\build\* backend\src\main\resources\static\

echo [3/3] 编译后端...
cd backend
call mvn clean package -DskipTests -q
echo 启动服务...
for %%f in (target\slay-the-spire-clone-*.jar) do set JAR=%%f
java -jar %JAR%
