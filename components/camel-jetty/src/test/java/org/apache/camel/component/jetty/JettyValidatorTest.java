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
package org.apache.camel.component.jetty;

import java.io.InputStream;

import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JettyValidatorTest extends CamelTestSupport {

    private int port;

    @Test
    void testValidRequest() {
        InputStream inputStream = this.getClass().getResourceAsStream("ValidRequest.xml");
        assertNotNull(inputStream, "The inputStream should not be null");

        String response = template.requestBody("http://localhost:" + port + "/test", inputStream, String.class);

        assertEquals("<ok/>", response, "The response should be ok");
    }

    @Test
    void testInvalidRequest() {
        InputStream inputStream = this.getClass().getResourceAsStream("InvalidRequest.xml");
        assertNotNull(inputStream, "The inputStream should not be null");

        String response = template.requestBody("http://localhost:" + port + "/test", inputStream, String.class);
        assertEquals("<error/>", response, "The response should be error");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
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
