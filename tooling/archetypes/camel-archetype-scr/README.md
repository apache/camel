camel-archetype-scr
===================

To install the archetype run

    mvn install
    
To generate a Camel SCR bundle project run

    mvn archetype:generate -Dfilter=org.apache.camel.archetypes:camel-archetype-scr
    Choose archetype:
    1: local -> org.apache.camel.archetypes:camel-archetype-scr (Creates a new Camel SCR bundle project for Karaf)
    Choose a number or apply filter (format: [groupId:]artifactId, case sensitive contains): : 1
    Define value for property 'groupId': : my.example
    Define value for property 'artifactId': : my-test
    Define value for property 'version':  1.0-SNAPSHOT: :
    Define value for property 'package':  my.example: :
    [INFO] Using property: camel-version = 2.15-SNAPSHOT
    Define value for property 'className': : MyTest
    [INFO] Using property: commons-lang-version = 2.6
    [INFO] Using property: log4j-version = 1.2.17
    [INFO] Using property: maven-bundle-plugin-version = 2.3.7
    [INFO] Using property: maven-compiler-plugin-version = 2.5.1
    [INFO] Using property: maven-javadoc-plugin-version = 2.9.1
    [INFO] Using property: maven-release-plugin-version = 2.5
    [INFO] Using property: maven-resources-plugin-version = 2.6
    [INFO] Using property: maven-scm-provider-gitexe-version = 1.9
    [INFO] Using property: maven-scr-plugin-version = 1.19.0
    [INFO] Using property: maven-source-plugin-version = 2.3
    [INFO] Using property: slf4j-version = 1.7.7
    [INFO] Using property: versions-maven-plugin-version = 2.1
    Confirm properties configuration:
    groupId: my.example
    artifactId: my-test
    version: 1.0-SNAPSHOT
    package: my.example
    camel-version: 2.15-SNAPSHOT
    className: MyTest
    commons-lang-version: 2.6
    log4j-version: 1.2.17
    maven-bundle-plugin-version: 2.3.7
    maven-compiler-plugin-version: 2.5.1
    maven-javadoc-plugin-version: 2.9.1
    maven-release-plugin-version: 2.5
    maven-resources-plugin-version: 2.6
    maven-scm-provider-gitexe-version: 1.9
    maven-scr-plugin-version: 1.19.0
    maven-source-plugin-version: 2.3
    slf4j-version: 1.7.7
    versions-maven-plugin-version: 2.1
     Y: :

     
All done! Check ReadMe.txt in the generated project folder for the next steps.
