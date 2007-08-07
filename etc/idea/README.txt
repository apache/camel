
To setup an IntelliJ project: (tested with version 5.1.2)

1) From the trunk, run "mvn process-test-sources -Pnochecks idea:idea"

2) From IntelliJ, open the project created above

3) From IntelliJ, select "File -> Import Settings".  Navigate to this folder
   and sellect the settings.jar file.  Let it import everything.  Restart.

4) From IntelliJ, select "File -> Settings", select "Project Code Style", 
   "Use per-project code style","Import", and select the "CXFStyle" option.

Optional:
5) If you have the "JetStyle" plugin installed, select "File -> Settings", 
   "Checkstyle", "Select Checkstyle Configuration", navigate to trunk/checkstyle.xml.






