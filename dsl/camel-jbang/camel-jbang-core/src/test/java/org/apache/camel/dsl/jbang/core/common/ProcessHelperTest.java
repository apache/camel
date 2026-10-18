/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.common;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The name the CLI shows for a running integration (camel ps, camel log and the other commands).
 */
class ProcessHelperTest {

    private static final String MAVEN
            = "/usr/bin/java -classpath plexus-classworlds-2.8.0.jar org.codehaus.plexus.classworlds.launcher.Launcher";

    @Test
    void mainClassOfTheIntegration() {
        JsonObject root = root("camel-1");
        root.put("runtime", new JsonObject(Map.of("mainClass", "com.acme.OrdersApplication")));

        assertThat(ProcessHelper.extractName(root, process("java -jar orders.jar", null)))
                .isEqualTo("com.acme.OrdersApplication");
    }

    @Test
    void routeFilesOfCamelRun() {
        ProcessHandle ph = process(
                "java -cp camel-launcher.jar main.CamelJBang run src/hello.camel.yaml README.md Foo.java application.properties",
                null);

        // only the route files, without their directory
        assertThat(ProcessHelper.extractName(root("CamelJBang"), ph)).isEqualTo("hello.camel.yaml Foo.java");
    }

    @Test
    void mainClassOnTheCommandLine() {
        assertThat(ProcessHelper.extractName(root("camel-1"), process("java -cp lib/* com.acme.Main", null)))
                .isEqualTo("com.acme.Main");
    }

    @Test
    void nameOfTheCamelContextWithoutACommandLine() {
        assertThat(ProcessHelper.extractName(root("orders"), process(null, null))).isEqualTo("orders");
        assertThat(ProcessHelper.extractName(root("orders"), null)).isEqualTo("orders");
        assertThat(ProcessHelper.extractName(null, null)).isEmpty();
    }

    @Test
    void springBootRunIsTheProcessMavenStarted() {
        ProcessHandle maven = process(MAVEN + " spring-boot:run", null);
        ProcessHandle app = process("java -cp camel-spring-boot-4.24.0.jar com.acme.Application", maven);
        when(maven.children()).thenAnswer(inv -> Stream.of(app));

        // Camel runs in the process Maven started, so Maven itself has no name to be listed with
        assertThat(ProcessHelper.extractName(null, maven)).isEmpty();
        assertThat(ProcessHelper.extractName(null, app)).isEqualTo("com.acme.Application");
    }

    @Test
    void quarkusDevIsNamedAfterTheMavenGoal() {
        ProcessHandle maven = process(MAVEN + " quarkus:dev", null);
        ProcessHandle app = process("java -cp camel-quarkus-core-3.30.0.jar -jar quarkus-run.jar", maven);

        assertThat(ProcessHelper.extractName(null, app)).isEqualTo("quarkus:dev");
    }

    private static JsonObject root(String contextName) {
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", contextName)));
        return root;
    }

    private static ProcessHandle process(String commandLine, ProcessHandle parent) {
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(info.commandLine()).thenReturn(Optional.ofNullable(commandLine));
        ProcessHandle ph = mock(ProcessHandle.class);
        when(ph.info()).thenReturn(info);
        when(ph.parent()).thenReturn(Optional.ofNullable(parent));
        when(ph.children()).thenAnswer(inv -> Stream.empty());
        return ph;
    }
}
