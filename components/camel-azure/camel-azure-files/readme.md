# Camel Azure Files Component

This component allows to access Azure Files over public Internet.
It reasonably mimics the (S)FTP(S) components.
It uses Azure Java SDK.

## State

In development since 2023-05, so far experimental.

## Component Documentation

The source of [the component doc fragment](src/main/docs/azure-files-component.adoc)
is somehow usable as is. 

The missing fragments can be deducted by `@UriParam` in java source,
the File component doc (many options are inherited), etc.  

## History

Formulated requirement in https://issues.apache.org/jira/browse/CAMEL-19279.

Forked Git as https://github.com/pekuz/camel/tree/camel-3.x/components/camel-azure/camel-azure-files
and started blending camel-ftp, camel-azure-storage-blob
with intent to support Azure Files. 

FTPFile is replaced by ShareFileItem.

Adapted connection management in the operations class. Azure Java SDK
manages connections internally. It might be possible to integrate
down at the connection provider level but we are not targeting it upfront
because FTP protocol uses another session and connection(s) management
concept than HTTP-based protocols. 

Initially I had hoped, I could reuse some classes as-is but in
reality a copy-and-paste has been needed because of FTPFile use
(despite impl needs only: file name, last modified and length). 

A set of problems with mvn generate:
 
  - CAMEL-19379 unclear error message (Resolved & closed)
  - CAMEL-19385 [Windows] generate Error loading other model. Reason: FirstVersion is not specified.

While cloning the fork in Github Desktop, I had selected "I want to contribute mode",
and it updated upstream to fork's upstream i.e. https://github.com/apache/camel.git
it's likely at cause of subsequent problems so changing the upstream back
to https://github.com/pekuz/camel.git

The endpoint path interpretation is specialized
to non-uniform /share[/directory]. 

Endpoint parameters cleanup, removed those FTP-specific: 

  - ftpClient
  - ftpClientConfig
  - passive
  - passiveMode
  - stepwise
  - binary
  - charset
  - account
  - siteCommand
  - chmod
  - fastExistsCheck
  - handleDirectoryParserAbsoluteResult
  - separator
  - sendNoop
  - bufferSize
  
or Azure Files irrelevant:
  
  - username
  - password
  
or not implemented:

  - transferLoggingLevel
  - transferLoggingIntervalSeconds
  - transferLoggingVerbose
  - soTimeout
  - useList
  - ignoreFileNotFoundOrPermissionError
  - moveExisting

Basic `to("azure-files://...")` does not crash. 

The component is tested with 1 GiB upload
(it took 2h47m47s over 1 Mbit/s uplink)
and download.

## Deps

At first I want to use the Azure Files component with Camel 3.16+
hence Java 11 is selected as a base dependency.

https://camel.apache.org/manual/what-are-the-dependencies.html

Java 17, and Camel 4 port, might come later after Java 17 and its tools
chain is approved by our security team.

### Eclipse

Eclipse 2023-03 detects Java version to be used for executing maven from 
the `requireJavaVersion` element of the `maven-enforcer-plugin` configuration. 
          
https://github.com/eclipse-m2e/m2e-core/blob/master/RELEASE_NOTES.md#220

Investigating the component pom's parents chain, I have found at the top-most parent pom:

    <groupId>org.apache</groupId>
    <artifactId>apache</artifactId>
    <version>29</version>

    <properties>    
      <minimalJavaBuildVersion>1.8</minimalJavaBuildVersion>

    <execution>
      <id>enforce-java-version</id>
      <goals>
        <goal>enforce</goal>
      </goals>
      <configuration>
        <rules>
          <requireJavaVersion>
            <version>${minimalJavaBuildVersion}</version>
          </requireJavaVersion>
        </rules>
      </configuration>
    </execution>

consequently it is better to specify in the component pom:

    <properties>
        <minimalJavaBuildVersion>11</minimalJavaBuildVersion>
        
to get a warning if eclipse m2e selected a lower JDK version:

    [[1;34mINFO[m] [1m--- [0;32menforcer:3.0.0:enforce[m [1m(enforce-java-version)[m @ [36mcamel-azure-files[0;1m ---[m
    [[1;33mWARNING[m] Rule 0: org.apache.maven.plugins.enforcer.RequireJavaVersion failed with message:
    Detected JDK Version: 1.8.0-271 is not in the allowed range 11.
    
Reported as CAMEL-19384.

