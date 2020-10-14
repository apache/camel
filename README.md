# Apache Camel

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.camel/apache-camel/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.apache.camel/apache-camel)
[![Javadocs](http://www.javadoc.io/badge/org.apache.camel/apache-camel.svg?color=brightgreen)](http://www.javadoc.io/doc/org.apache.camel/camel-api)
[![Stack Overflow](https://img.shields.io/:stack%20overflow-apache--camel-brightgreen.svg)](http://stackoverflow.com/questions/tagged/apache-camel)
[![Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://camel.zulipchat.com/)
[![Master Build](https://github.com/apache/camel/workflows/build/badge.svg)](https://github.com/apache/camel/actions?query=workflow%3A%22build%22)
[![Twitter](https://img.shields.io/twitter/follow/ApacheCamel.svg?label=Follow&style=social)](https://twitter.com/ApacheCamel)

[Apache Camel](http://camel.apache.org/) is a powerful, open-source integration framework based on prevalent 
Enterprise Integration Patterns with powerful bean integration.

### Introduction

Camel enables the creation of the Enterprise Integration Patterns to implement routing
and mediation rules in either a Java-based Domain Specific Language (or Fluent API),
via Spring or Blueprint based Xml Configuration files, or via the Scala DSL.
That means you get smart completion of routing rules in your IDE whether
in your Java, Scala, or XML editor.

Apache Camel uses URIs to enable easier integration with all kinds of
transport or messaging model including HTTP, ActiveMQ, JMS, JBI, SCA, MINA
or CXF together with working with pluggable Data Format options.
Apache Camel is a small library that has minimal dependencies for easy embedding
in any Java application. Apache Camel lets you work with the same API regardless of the 
transport type, making it possible to interact with all the components provided out-of-the-box, 
with a good understanding of the API.

Apache Camel has powerful Bean Binding and integrated seamlessly with
popular frameworks such as Spring, CDI, and Blueprint.

Apache Camel has extensive testing support allowing you to easily
unit test your routes.

## Components

Apache Camel comes alongside several artifacts with components, data formats, languages, and kinds.
The up to date list is available online at the Camel website:

* Components: <https://camel.apache.org/components/latest/>
* Data Formats: <https://camel.apache.org/components/latest/dataformats/>
* Languages: <https://camel.apache.org/components/latest/languages/>
* Miscellaneous: <https://camel.apache.org/components/latest/#_miscellaneous_components>

## Examples

Apache Camel comes with many examples.
The up to date list is available online at GitHub:

* Examples: <https://github.com/apache/camel-examples/tree/master/examples#welcome-to-the-apache-camel-examples>

## Getting Started

To help you get started, try the following links:

**Getting Started**

<http://camel.apache.org/getting-started.html>

The beginner examples are another powerful alternative pathway for getting started with Apache Camel.

* Examples: <https://github.com/apache/camel-examples/tree/master/examples#welcome-to-the-apache-camel-examples>

**Building**

<http://camel.apache.org/building.html>

**Contributions**

We welcome all kinds of contributions, the details of which are specified here:

<https://github.com/apache/camel/blob/master/CONTRIBUTING.md>


Please refer to the website for details of finding the issue tracker, 
email lists, GitHub, chat

Website: <http://camel.apache.org/>

Github (source): <https://github.com/apache/camel>

Issue tracker: <https://issues.apache.org/jira/projects/CAMEL>

Mailing-list: <http://camel.apache.org/mailing-lists.html>

Chat: <https://camel.zulipchat.com/>

StackOverflow: <https://stackoverflow.com/questions/tagged/apache-camel>

Twitter: <https://twitter.com/ApacheCamel>


**Support**

For additional help, support, we recommend referencing this page first:

<http://camel.apache.org/support.html>

**Getting Help**

If you get stuck somewhere, please feel free to reach out to us on either StackOverflow, Chat, or the email mailing list.

Please help us make Apache Camel better - we appreciate any feedback
you may have.

Enjoy!

-----------------
The Camel riders!

# Licensing

The terms for software licensing are detailed in the `LICENSE.txt` file,  
located in the working directory.

This distribution includes cryptographic software.  The country in
which you currently reside may levy restrictions on the import,
possession, use, and re-export to foreign countries of
encryption software.  BEFORE using any encryption software, please
check your country's laws, regulations, and policies concerning the
import, possession, or use, and re-export of encryption software, to
see if this is permitted.  See <http://www.wassenaar.org/> for more
information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS) has classified this software as Export Commodity
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
* **camel-infinispan** can be configured for secure communications
* **camel-jasypt** can be used for secure communications
* **camel-jetty** can be configured to use https
* **camel-mail** can be configured for secure communications
* **camel-nagios** can be configured for secure communications
* **camel-netty-http** can be configured to use https
* **camel-undertow** can be configured to use https
* **camel-xmlsecurity** can be configured for secure communications
