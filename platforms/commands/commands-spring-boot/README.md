# Camel Shell Commands for Spring Boot

This component is implemented as a plugin for [CRuSH Java Shell](http://www.crashub.org/), a component used by the Spring Boot platform for the remote shell.
It is essentially an adapter for the available Camel commands, responsible for passing through the options and arguments.

# Installation

To enable Spring Boot remote shell support:

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-remote-shell</artifactId>
    <version>x.x.x</version>
    <!-- use the version that is used as the depndency by the Camel -->
</dependency>

```

To enable Camel Commands for the Spring Boot remote shell:

```xml

<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-commands-spring-boot</artifactId>
    <version>x.x.x</version>
    <!-- use the same version as your Camel core version -->
</dependency>

```


