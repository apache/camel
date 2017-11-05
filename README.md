# Apache Camel

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.camel/apache-camel/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.apache.camel/apache-camel)
[![Javadocs](http://www.javadoc.io/badge/org.apache.camel/apache-camel.svg?color=brightgreen)](http://www.javadoc.io/doc/org.apache.camel/camel-core)
[![Stack Overflow](https://img.shields.io/:stack%20overflow-apache--camel-brightgreen.svg)](http://stackoverflow.com/questions/tagged/apache-camel)
[![Gitter](https://img.shields.io/gitter/room/apache/apache-camel.js.svg)](https://gitter.im/apache/apache-camel)
 

[Apache Camel](http://camel.apache.org/) is a powerful open source integration framework based on known
Enterprise Integration Patterns with powerful bean integration.

### Introduction

Camel lets you create the Enterprise Integration Patterns to implement routing
and mediation rules in either a Java based Domain Specific Language (or Fluent API),
via Spring or Blueprint based Xml Configuration files, or via the Scala DSL.
This means you get smart completion of routing rules in your IDE whether
in your Java, Scala or XML editor.

Apache Camel uses URIs so that it can easily work directly with any kind of
transport or messaging model such as HTTP, ActiveMQ, JMS, JBI, SCA, MINA
or CXF together with working with pluggable Data Format options.
Apache Camel is a small library which has minimal dependencies for easy embedding
in any Java application. Apache Camel lets you work with the same API regardless
which kind of transport used, so learn the API once and you will be able to
interact with all the Components that is provided out-of-the-box.

Apache Camel has powerful Bean Binding and integrated seamless with
popular frameworks such as Spring, CDI, Blueprint and Guice.

Apache Camel has extensive testing support allowing you to easily
unit test your routes.

## Components

Apache Camel comes with a lot of artifacts with components, data formats, languages and various other kinds.
The up to date list is available online at:

* Components: <https://github.com/apache/camel/tree/master/components#components>
* Data Formats: <https://github.com/apache/camel/tree/master/components#data-formats>
* Languages: <https://github.com/apache/camel/tree/master/components#expression-languages>
* Miscellaneous: <https://github.com/apache/camel/tree/master/components#miscellaneous-components>

## Examples

Apache Camel comes with many examples.
The up to date list is available online at:

* Examples: <https://github.com/apache/camel/tree/master/examples#examples>

## Getting Started

To help you get started, try the following links:

**Getting Started**
  
<http://camel.apache.org/getting-started.html>

It is also recommended to get started by trying out some of the beginner examples.

* Examples: <https://github.com/apache/camel/tree/master/examples#examples>

**Building**
  
<http://camel.apache.org/building.html>

**Contributions**

We welcome contributions of all kinds, for details of how you can help
  
<https://github.com/apache/camel/blob/master/CONTRIBUTING.md>

Please refer to the website for details of finding the issue tracker, 
email lists, IRC and Gitter chat
  
Website: <http://camel.apache.org/>

Github: <https://github.com/apache/camel>

Mailing list: <http://camel.apache.org/mailing-lists.html>

IRC chat: <http://camel.apache.org/irc-room.html>

Gitter chat: <https://gitter.im/apache/apache-camel>


**Support**

If you need support or help then read this page first
  
<http://camel.apache.org/support.html>

**Forum**

If you hit any problems please talk to us on the Camel Forums
  
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback 
you may have.

Enjoy!

-----------------
The Camel riders!

# Licensing

This software is licensed under the terms you may find in the file
named `LICENSE.txt` in this directory.
   
This distribution includes cryptographic software.  The country in
which you currently reside may have restrictions on the import,
possession, use, and/or re-export to another country, of
encryption software.  BEFORE using any encryption software, please
check your country's laws, regulations and policies concerning the
import, possession, or use, and re-export of encryption software, to
see if this is permitted.  See <http://www.wassenaar.org/> for more
information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity
Control Number (ECCN) 5D002.C.1, which includes information security
software using or performing cryptographic functions with asymmetric
algorithms.  The form and manner of this Apache Software Foundation
distribution makes it eligible for export under the License Exception
ENC Technology Software Unrestricted (TSU) exception (see the BIS
Export Administration Regulations, Section 740.13) for both object
code and source code.

The following provides more details on the included cryptographic
software:

* **camel-ahc** can be configured to use https
* **camel-atmosphere-websocket** can be used for secure communications
* **camel-crypto** can be used for secure communications
* **camel-cxf** can be configured for secure communications
* **camel-ftp** can be configured for secure communications
* **camel-http** can be configured to use https
* **camel-http4** can be configured to use https
* **camel-infinispan** can be configured for secure communications
* **camel-jasypt** can be used for secure communications
* **camel-jetty** can be configured to use https
* **camel-mail** can be configured for secure communications
* **camel-nagios** can be configured for secure communications
* **camel-netty-http** can be configured to use https
* **camel-netty4-http** can be configured to use https
* **camel-undertow** can be configured to use https
* **camel-xmlsecurity** can be configured for secure communications

