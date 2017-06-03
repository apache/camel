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
package org.apache.camel.component.sparkrest;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RestConfigurationCamelSparkTest extends BaseSparkTest {

    @Test
    public void testSparkHello() throws Exception {
        String out = template.requestBody("http://localhost:" + port + "/spark/hello", null, String.class);
        assertEquals("Hello World", out);
    }

    @Test
    public void testSparkBye() throws Exception {
        String out = template.requestBody("http://localhost:" + port + "/spark/bye", null, String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testSparkPost() throws Exception {
        getMockEndpoint("mock:update").expectedBodiesReceived("I did this");

        template.requestBody("http://localhost:" + port + "/spark/bye", "I did this", String.class);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure port on rest configuration which spark-rest will pickup and use
                restConfiguration().component("spark-rest").port(port);

                // will automatic find the spark component to use, as we setup that component in the BaseSparkTest
                rest("/spark/hello")
                    .get().to("direct:hello");

                rest("/spark/bye")
                    .get().to("direct:bye")
                    .post().to("mock:update");

                from("direct:hello")
                    .transform().constant("Hello World");

                from("direct:bye")
                    .transform().constant("Bye World");
            }
        };
    }
}
