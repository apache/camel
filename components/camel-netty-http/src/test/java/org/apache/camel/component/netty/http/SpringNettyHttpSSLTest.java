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
package org.apache.camel.component.netty.http;

import java.net.URL;

import javax.annotation.Resource;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/org/apache/camel/component/netty/http/SpringNettyHttpSSLTest.xml"})
public class SpringNettyHttpSSLTest extends Assert {

    @Produce
    private ProducerTemplate template;

    @EndpointInject("mock:input")
    private MockEndpoint mockEndpoint;

    private Integer port;

    public Integer getPort() {
        return port;
    }

    @Resource(name = "dynaPort")
    public void setPort(Integer port) {
        this.port = port;
    }

    @BeforeClass
    public static void setUpJSSE() throws Exception {
        // ensure JSSE clients can validate the self signed dummy localhost cert,
        // use the server keystore as the trust store for these tests
        URL trustStoreUrl = NettyHttpSSLTest.class.getClassLoader().getResource("jsse/localhost.p12");
        System.setProperty("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    @AfterClass
    public static void tearDownJSSE() throws Exception {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
    }

    @Test
    public void testSSLInOutWithNettyConsumer() throws Exception {
        mockEndpoint.expectedBodiesReceived("Hello World");

        String out = template.requestBody("https://localhost:" + getPort(), "Hello World", String.class);
        assertEquals("Bye World", out);

        mockEndpoint.assertIsSatisfied();
    }

}

