@echo off
echo ==================================================================
echo             ExamCraft 3D: Server Launch Script
echo ==================================================================

if not exist out (
    echo Binaries not found. Building first...
    call build.bat
)

set PORT=8080
if not "%~1"=="" set PORT=%~1

echo Starting ExamCraft 3D on port %PORT%...
echo Navigate to: http://localhost:%PORT%/
echo Press Ctrl+C to terminate the server.
echo.

java -cp out Main --port %PORT%
