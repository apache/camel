/**
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
package org.apache.camel.component.jetty9;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty9.springboot.JettyHttpComponentAutoConfiguration9;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

/**
 * Testing the ssl configuration
 */
@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@ContextConfiguration(classes = {JettyHttpComponentAutoConfiguration9.class, CamelAutoConfiguration.class})
@SpringBootTest(properties = {
        "camel.ssl.config.cert-alias=web",
        "camel.ssl.config.key-managers.key-password=changeit",
        "camel.ssl.config.key-managers.key-store.resource=/keystore.p12",
        "camel.ssl.config.key-managers.key-store.password=changeit",
        "camel.ssl.config.key-managers.key-store.type=PKCS12",
        "camel.ssl.config.trust-managers.key-store.resource=/cacerts",
        "camel.ssl.config.trust-managers.key-store.password=changeit",
        "camel.ssl.config.trust-managers.key-store.type=jks",
        "camel.component.jetty.use-global-ssl-context-parameters=true",
        "camel.component.http4.use-global-ssl-context-parameters=true"
})
@Ignore("Bug in https4 spring-boot configuration")
public class Jetty9SSLTest {

    private static int port;

    @Autowired
    private ProducerTemplate producerTemplate;

    @BeforeClass
    public static void init() {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Test
    public void testEndpoint() throws Exception {
        String result = producerTemplate.requestBody("https4://localhost:" + port, null, String.class);
        assertEquals("Hello", result);
    }

    @Test
    public void testEndpointWithJettyProducer() throws Exception {
        String result = producerTemplate.requestBody("jetty:https://localhost:" + port, null, String.class);
        assertEquals("Hello", result);
    }

    @Component
    public static class TestRoutes extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("jetty:https://localhost:" + port)
                    .transform().constant("Hello");
        }
    }

}

