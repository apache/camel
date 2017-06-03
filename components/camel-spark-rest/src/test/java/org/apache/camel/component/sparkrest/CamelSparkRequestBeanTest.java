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
import spark.Request;

public class CamelSparkRequestBeanTest extends BaseSparkTest {

    @Test
    public void testSparkGet() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);

        String out = template.requestBody("http://localhost:" + getPort() + "/hello/camel/to/world", null, String.class);
        assertEquals("Bye big world from camel", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("spark-rest:get:/hello/*/to/*")
                        .to("mock:foo")
                        .bean(CamelSparkRequestBeanTest.class, "doSomething");
            }
        };
    }

    public String doSomething(Request request) {
        return "Bye big " + request.splat()[1] + " from " + request.splat()[0];
    }
}
