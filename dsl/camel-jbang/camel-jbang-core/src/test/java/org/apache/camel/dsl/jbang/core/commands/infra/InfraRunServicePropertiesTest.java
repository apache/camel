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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-22083: camel infra run names the connection details of a service as the options of the Camel component, not as
 * the Java methods of the service.
 */
class InfraRunServicePropertiesTest {

    /**
     * A service as most of test-infra has it: the methods named as the options of the component, and the getters of the
     * tests of the components.
     */
    public interface SampleInfraService {

        @Deprecated
        int getPort();

        int port();

        @Deprecated
        String hostname();

        String host();

        String getUsername();

        String getFtpRootDir();

        int getCQL3Port();

        String getServiceBaseURL();

        boolean isRunning();

        String uiUrl();

        String greeting(String name);

        void registerProperties();
    }

    public static class SampleService implements SampleInfraService {

        @Override
        public int getPort() {
            return 2121;
        }

        @Override
        public int port() {
            return 2121;
        }

        @Override
        public String hostname() {
            return "localhost";
        }

        @Override
        public String host() {
            return "localhost";
        }

        @Override
        public String getUsername() {
            return "admin";
        }

        @Override
        public String getFtpRootDir() {
            return "/tmp/ftp";
        }

        @Override
        public int getCQL3Port() {
            return 9042;
        }

        @Override
        public String getServiceBaseURL() {
            return "http://localhost:8080/fhir";
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public String uiUrl() {
            // no UI: not in the details
            return null;
        }

        @Override
        public String greeting(String name) {
            return "Hello " + name;
        }

        @Override
        public void registerProperties() {
            // not a detail of the service
        }
    }

    @Test
    void theDetailsAreNamedAsProperties() {
        Map<String, Object> properties = InfraRun.serviceProperties(SampleInfraService.class, new SampleService());

        assertThat(properties).containsExactly(
                Map.entry("cql3Port", 9042),
                Map.entry("ftpRootDir", "/tmp/ftp"),
                Map.entry("host", "localhost"),
                Map.entry("port", 2121),
                Map.entry("running", true),
                Map.entry("serviceBaseURL", "http://localhost:8080/fhir"),
                Map.entry("username", "admin"));
    }

    @Test
    void propertyNames() {
        assertThat(InfraRun.propertyName("getPort")).isEqualTo("port");
        assertThat(InfraRun.propertyName("getBootstrapServers")).isEqualTo("bootstrapServers");
        assertThat(InfraRun.propertyName("getCQL3Endpoint")).isEqualTo("cql3Endpoint");
        assertThat(InfraRun.propertyName("getURL")).isEqualTo("url");
        assertThat(InfraRun.propertyName("isRunning")).isEqualTo("running");
        // not a getter
        assertThat(InfraRun.propertyName("brokers")).isEqualTo("brokers");
        assertThat(InfraRun.propertyName("get")).isEqualTo("get");
        assertThat(InfraRun.propertyName("getaway")).isEqualTo("getaway");
        assertThat(InfraRun.propertyName("island")).isEqualTo("island");
    }
}
