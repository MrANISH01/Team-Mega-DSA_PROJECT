@echo off
echo ==================================================================
echo             ExamCraft 3D: Automated Test Suite Runner
echo ==================================================================

if not exist out (
    echo Binaries not found. Building first...
    call build.bat
)

java -cp out test.EdgeCaseTests
