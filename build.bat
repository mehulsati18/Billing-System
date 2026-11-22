@echo off
REM ===== Step 1: compile and make the JAR =====
javac BillingSystem.java
if errorlevel 1 (echo Compile failed & pause & exit /b 1)
echo Main-Class: BillingSystem> manifest.txt
jar cfm BillingSystem.jar manifest.txt *.class
echo.
echo DONE: BillingSystem.jar created. Double-click it to run.
echo.
REM ===== Step 2 (optional): make a Windows installer (.exe) =====
REM Needs JDK 17+ and WiX Toolset 3.x installed. Remove "REM" below to enable.
REM jpackage --type exe --name "Billing System" --input . --main-jar BillingSystem.jar --main-class BillingSystem --win-shortcut --win-menu --win-dir-chooser
pause
