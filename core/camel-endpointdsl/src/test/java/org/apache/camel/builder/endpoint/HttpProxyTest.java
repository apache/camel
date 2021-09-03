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
package org.apache.camel.builder.endpoint;

import java.util.Properties;

import org.apache.camel.component.http.HttpEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpProxyTest extends BaseEndpointDslTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGiven() throws Exception {
        Properties props = new Properties();
        props.put("prop.proxyHost", "myproxy");
        props.put("prop.proxyPort", "3280");
        context.getPropertiesComponent().setInitialProperties(props);

        context.start();

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to(https("hello-world")
                                .proxyHost("{{prop.proxyHost}}")
                                .proxyPort("{{prop.proxyPort}}"));
            }
        });

        HttpEndpoint he = (HttpEndpoint) context.getEndpoints().stream().filter(e -> e instanceof HttpEndpoint).findFirst()
                .orElse(null);
        assertEquals("myproxy", he.getProxyHost());
        assertEquals(3280, he.getProxyPort());
        assertEquals("https://hello-world?proxyHost=myproxy&proxyPort=3280", he.getEndpointUri());

        context.stop();
    }

    @Test
    public void testOptional() throws Exception {
        Properties props = new Properties();
        props.put("prop.proxyHost", "myproxy");
        context.getPropertiesComponent().setInitialProperties(props);

        context.start();

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to(https("hello-world")
                                .proxyHost("{{?prop.proxyHost}}")
                                .proxyPort("{{?prop.proxyPort}}"));
            }
        });

        HttpEndpoint he = (HttpEndpoint) context.getEndpoints().stream().filter(e -> e instanceof HttpEndpoint).findFirst()
                .orElse(null);
        assertEquals("myproxy", he.getProxyHost());
        assertEquals(0, he.getProxyPort());
        assertEquals("https://hello-world?proxyHost=myproxy", he.getEndpointUri());

        context.stop();
    }

}
