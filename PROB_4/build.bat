@echo off
echo ==================================================================
echo             ExamCraft 3D: Build Script (Windows)
echo ==================================================================
if not exist out mkdir out
if not exist output mkdir output

echo Compiling Java source files...
javac --add-modules jdk.httpserver -d out src/Main.java src/json/*.java src/model/*.java src/algorithm/*.java src/validation/*.java src/export/*.java src/benchmark/*.java src/server/*.java src/test/*.java

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Compilation finished with 0 errors.
) else (
    echo [ERROR] Compilation failed.
    exit /b %ERRORLEVEL%
)
