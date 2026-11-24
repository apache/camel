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
package org.apache.camel.main;

import java.util.List;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.util.SecurityViolation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The security policy checks the properties of the beans that the routes define (such as the beans section of a YAML or
 * XML DSL file) under the key of a bean configured from properties: camel.beans.&lt;name&gt;.&lt;property&gt;.
 */
class MainSecurityPolicyBeansTest {

    @Test
    void insecureBeanPropertyFailsStartup() {
        Main main = new Main();
        main.addInitialProperty("camel.security.insecureSslPolicy", "fail");
        main.configure().addRoutesBuilder(routeWithBean("myClient", Map.of("trustAllCertificates", "true")));

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("[insecure:ssl]"), ex.getMessage());
        assertTrue(ex.getMessage().contains("[camel.beans.myClient.trustAllCertificates]"), ex.getMessage());
    }

    @Test
    void insecureBeanPropertyFailsStartupWithProdProfile() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.configure().addRoutesBuilder(routeWithBean("myClient", Map.of("trustAllCertificates", "true")));

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("[camel.beans.myClient.trustAllCertificates]"), ex.getMessage());
    }

    @Test
    void secureBeanPropertyStarts() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.configure().addRoutesBuilder(routeWithBean("myClient", Map.of("trustAllCertificates", "false")));

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    void allowedBeanPropertyStarts() {
        Main main = new Main();
        main.addInitialProperty("camel.main.profile", "prod");
        main.addInitialProperty("camel.security.allowedProperties", "camel.beans.myClient.trustAllCertificates");
        main.configure().addRoutesBuilder(routeWithBean("myClient", Map.of("trustAllCertificates", "true")));

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    void plainTextSecretOnBeanFailsStartup() {
        Main main = new Main();
        main.addInitialProperty("camel.security.secretPolicy", "fail");
        main.configure().addRoutesBuilder(routeWithBean("myClient", Map.of("password", "plaintext-password")));

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("[secret]"), ex.getMessage());
        assertTrue(ex.getMessage().contains("[camel.beans.myClient.password]"), ex.getMessage());
    }

    @Test
    void secretFromPlaceholderOnBeanStarts() {
        Main main = new Main();
        main.addInitialProperty("camel.security.secretPolicy", "fail");
        main.configure().addRoutesBuilder(routeWithBean("myClient", Map.of("password", "{{vault:my-secret}}")));

        assertDoesNotThrow(() -> {
            main.start();
            main.stop();
        });
    }

    @Test
    void nestedBeanPropertyFailsStartup() {
        Main main = new Main();
        main.addInitialProperty("camel.security.insecureSslPolicy", "fail");
        main.configure().addRoutesBuilder(
                routeWithBean("myClient", Map.of("tls", Map.of("trustAllCertificates", "true"))));

        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class, main::start);
        assertTrue(ex.getMessage().contains("[camel.beans.myClient.tls.trustAllCertificates]"), ex.getMessage());
    }

    @Test
    void warnedBeanPropertyIsReportedWithTheConfigurationProperties() {
        // the default policy is warn
        Main main = new Main();
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");
        main.configure().addRoutesBuilder(routeWithBean("myClient", Map.of("trustAllCertificates", "true")));
        main.start();
        try {
            SecurityPolicyResult result = main.getCamelContext().getCamelContextExtension()
                    .getContextPlugin(SecurityPolicyResult.class);
            assertNotNull(result);
            List<String> keys = result.getViolations().stream().map(SecurityViolation::propertyKey).toList();
            assertEquals(List.of("camel.ssl.trustAllCertificates", "camel.beans.myClient.trustAllCertificates"), keys);
        } finally {
            main.stop();
        }
    }

    private static RouteBuilder routeWithBean(String name, Map<String, Object> properties) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a bean as the YAML and XML DSL define it, from the beans section of the route file
                BeanFactoryDefinition<?> bean = new BeanFactoryDefinition<>();
                bean.setName(name);
                bean.setType("#class:" + MyClient.class.getName());
                bean.setProperties(properties);
                getBeans().add(bean);
            }
        };
    }

    public static class MyClient {

        private boolean trustAllCertificates;
        private String password;

        public boolean isTrustAllCertificates() {
            return trustAllCertificates;
        }

        public void setTrustAllCertificates(boolean trustAllCertificates) {
            this.trustAllCertificates = trustAllCertificates;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
