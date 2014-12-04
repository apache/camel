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
    [INFO] Using property: groupId = my.example
    Define value for property 'artifactId': : my-test
    Define value for property 'version':  1.0-SNAPSHOT: :
    Define value for property 'package':  my.example: :
    [INFO] Using property: archetypeArtifactId = camel-archetype-scr
    [INFO] Using property: archetypeGroupId = org.apache.camel.archetypes
    [INFO] Using property: archetypeVersion = 2.15-SNAPSHOT
    Define value for property 'className': : MyTest
    Confirm properties configuration:
    groupId: my.example
    groupId: my.example
    artifactId: my-test
    version: 1.0-SNAPSHOT
    package: my.example
    archetypeArtifactId: camel-archetype-scr
    archetypeGroupId: org.apache.camel.archetypes
    archetypeVersion: 2.15-SNAPSHOT
    className: MyTest
     Y: :
     
All done! Check ReadMe.txt in the generated project folder for the next steps.
