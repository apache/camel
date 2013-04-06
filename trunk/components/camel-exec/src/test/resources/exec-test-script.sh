echo $1
echo $JAVA_HOME

$JAVA_HOME/bin/java -cp $1 org.apache.camel.component.exec.ExecutableJavaProgram $2
