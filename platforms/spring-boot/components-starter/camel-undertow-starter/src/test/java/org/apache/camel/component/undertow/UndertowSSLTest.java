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
package org.apache.camel.component.undertow;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

/**
 * Testing the ssl configuration
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        UndertowSSLTest.TestConfiguration.class
    },
    properties = {
        "debug=false",
        "camel.ssl.config.cert-alias=web",
        "camel.ssl.config.key-managers.key-password=changeit",
        "camel.ssl.config.key-managers.key-store.resource=/keystore.p12",
        "camel.ssl.config.key-managers.key-store.password=changeit",
        "camel.ssl.config.key-managers.key-store.type=PKCS12",
        "camel.ssl.config.trust-managers.key-store.resource=/cacerts",
        "camel.ssl.config.trust-managers.key-store.password=changeit",
        "camel.ssl.config.trust-managers.key-store.type=jks",
        "camel.component.undertow.use-global-ssl-context-parameters=true"
    }
)
public class UndertowSSLTest {
    private static int port;

    @Autowired
    private ProducerTemplate producerTemplate;

    @BeforeClass
    public static void init() {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Test
    public void testEndpoint() throws Exception {
        String result = producerTemplate.requestBody("undertow:https://localhost:" + port, null, String.class);
        assertEquals("Hello", result);
    }

    @Component
    public static class TestRoutes extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("undertow:https://localhost:" + port)
                .transform().constant("Hello");
        }
    }

    @Configuration
    public static class TestConfiguration {
    }
}

