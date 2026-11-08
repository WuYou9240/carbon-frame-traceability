@echo off
chcp 65001 >nul
title 碳纤维车架质量溯源系统

REM 切到脚本所在目录：Spring Boot 从这里读 ./config/application.yml 私有配置
cd /d "%~dp0"

setlocal
set "PORT=18080"
set "JAR=%~dp0qts-backend\target\quality-trace-system-1.0.0.jar"

REM 本项目基于 Spring Boot 3，需要 JDK 17+。系统 PATH 里默认的 java 可能是 1.8，
REM 直接 java -jar 会报 UnsupportedClassVersionError，所以这里显式指定 JDK。
if not defined JAVA_HOME set "JAVA_HOME=C:\Users\92406\.jdks\openjdk-22.0.2-1"
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="

if not defined JAVA_HOME goto nojava
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAR%" goto nojar

echo ============================================================
echo   公路自行车碳纤维车架质量溯源系统
echo ------------------------------------------------------------
echo   访问地址：http://localhost:%PORT%/
echo   演示账号：admin / Qts@2026
echo   关闭本窗口即停止服务
echo ============================================================
echo.

java -jar "%JAR%" --server.port=%PORT%
goto end

:nojava
echo [错误] 未找到 JDK 17 及以上版本。
echo        请用记事本打开本文件，把 JAVA_HOME 改成你机器上的 JDK 目录。
pause
exit /b 1

:nojar
echo [错误] 未找到程序包：
echo        %JAR%
echo.
echo        请先在 qts-backend 目录执行：mvn clean package
pause
exit /b 1

:end
echo.
echo 服务已停止。
pause
