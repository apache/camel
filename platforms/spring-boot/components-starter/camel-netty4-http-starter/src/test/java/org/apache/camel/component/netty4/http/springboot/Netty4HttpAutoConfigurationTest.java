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
package org.apache.camel.component.netty4.http.springboot;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.apache.camel.component.netty4.http.springboot.Netty4StarterTestHelper.getPort;
import static org.junit.Assert.assertEquals;

/**
 * Testing the servlet mapping
 */
@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        Netty4HttpAutoConfigurationTest.TestConfiguration.class
    },
    properties = {
        "camel.component.netty4-http.configuration.compression=true"
})
public class Netty4HttpAutoConfigurationTest {

    @Autowired
    private ProducerTemplate producerTemplate;

    @Test
    public void testEndpoint() throws Exception {
        String result = producerTemplate.requestBody("netty4-http:http://localhost:" + getPort(), null, String.class);
        assertEquals("Hello", result);
    }

    @Test
    public void testConfigOverride() throws Exception {
        Exchange exchange = producerTemplate.request("netty4-http:http://localhost:" + getPort(), x -> x.getIn().setHeader("Accept-Encoding", "gzip"));
        Assert.assertEquals("gzip", exchange.getOut().getHeader("Content-Encoding"));
    }

    @Component
    public static class TestRoutes extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("netty4-http:http://localhost:" + getPort())
                    .transform().constant("Hello");
        }
    }

    @Configuration
    public static class TestConfiguration {
    }
}

