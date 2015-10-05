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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Test;

public class CamelSparkAcceptTest extends BaseSparkTest {

    @Test
    public void testSparkGet() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);

        try {
            template.requestBodyAndHeader("http://localhost:" + getPort() + "/hello", null, "Accept", "text/plain", String.class);
            fail("Should fail");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(404, cause.getStatusCode());
        }

        String out2 = template.requestBodyAndHeader("http://localhost:" + getPort() + "/hello", null, "Accept", "application/json", String.class);
        assertEquals("{ \"reply\": \"Bye World\" }", out2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("spark-rest:get:hello?accept=application/json")
                        .to("mock:foo")
                        .transform().constant("{ \"reply\": \"Bye World\" }");
            }
        };
    }
}
