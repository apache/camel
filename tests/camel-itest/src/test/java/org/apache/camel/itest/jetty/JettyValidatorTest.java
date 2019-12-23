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
package org.apache.camel.itest.jetty;

import java.io.InputStream;

import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JettyValidatorTest extends CamelTestSupport {

    private int port;

    @Test
    public void testValidRequest() throws Exception {
        InputStream inputStream = this.getClass().getResourceAsStream("ValidRequest.xml");
        assertNotNull("the inputStream should not be null", inputStream);

        String response = template.requestBody("http://localhost:" + port + "/test", inputStream, String.class);

        assertEquals("The response should be ok", response, "<ok/>");
    }

    @Test
    public void testInvalidRequest() throws Exception {
        InputStream inputStream = this.getClass().getResourceAsStream("InvalidRequest.xml");
        assertNotNull("the inputStream should not be null", inputStream);

        String response = template.requestBody("http://localhost:" + port + "/test", inputStream, String.class);
        assertEquals("The response should be error", response, "<error/>");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:" + port + "/test")
                    .convertBodyTo(String.class)
                    .to("log:in")
                    .doTry()
                        .to("validator:OptimizationRequest.xsd")
                        .transform(constant("<ok/>"))
                    .doCatch(ValidationException.class)
                        .transform(constant("<error/>"))
                    .end()
                    .to("log:out");
            }
        };
    }

}
