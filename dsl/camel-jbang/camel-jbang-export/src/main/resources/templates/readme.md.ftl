<#--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
# How to build and run a Camel application

This project was generated using [Camel Jbang](https://camel.apache.org/manual/camel-jbang.html). Please, refer to the online documentation for learning more about how to configure the export of your Camel application.

This is a brief guide explaining how to build, "containerize" and run your Camel application.

## For AI coding assistants

If you are an AI coding assistant working on this project, start from the Apache Camel LLM index at https://camel.apache.org/llms.txt.

- Any Apache Camel documentation page is available as LLM-friendly Markdown by replacing `.html` with `.md` in its URL.
- Use the Camel CLI and the Camel MCP server (linked from the index above) to look up components, their options and the catalog instead of guessing endpoint URIs and options.
- See `AGENTS.md` in this directory for project-specific guidance.

## Build the Maven project

```bash
./mvnw clean package
```

The application could now immediately run:

```bash
java -jar [=AppRuntimeJar]
```

## Create a Docker container

You can create a container image directly from the `src/main/docker` resources. Here you have a precompiled base configuration which can be enhanced with any further required configuration.

```bash
docker build -f src/main/docker/Dockerfile -t [=ArtifactId]:[=Version] .
```

Once the application is published, you can run it directly from the container:

```bash
docker run -it [=ArtifactId]:[=Version]
```

## Create a container with Jib plugin

If you don't want to deal with Docker resources, you can use the `jib` profile. This tool works inside Maven and is in charge to build and push a container
out of the box. The profile is configured to produce an executable jar (regardless of the runtime chosen). You can provide any argument expected by the plugin:

```bash
mvn clean package jib:build -Pjib \
    -Djib.to.image=my-registry.io/my-registry-org/my-container:latest \
    -Djib.from.image=eclipse-temurin:21-jdk \
    -Djib.container.user=1000
```

You can use any base image with a compatible JVM which provides a `java` executable in the path. Once the application is published,
you can run it directly from the container:

```bash
docker run my-registry.io/my-registry-org/my-container:latest
```
