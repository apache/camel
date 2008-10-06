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
package org.apache.camel.itest.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;

public class JettyValidatorTest extends ContextTestSupport {

    public void testValideRequest() throws Exception {
        InputStream inputStream = HttpClient.class.getResourceAsStream("ValidRequest.xml");
        assertNotNull("the inputStream should not be null", inputStream);
        String response = HttpClient.send(inputStream);
        assertEquals("The response should be ok", response, "<ok/>");
    }

    public void testInvalideRequest() throws Exception {
        InputStream inputStream = HttpClient.class.getResourceAsStream("InvalidRequest.xml");
        assertNotNull("the inputStream should not be null", inputStream);
        String response = HttpClient.send(inputStream);
        assertEquals("The response should be error", response, "<error/>");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:8192/test")
                    .setBody(body(String.class))
                    .to("log:in")
                    .tryBlock()
                        .to("validator:OptimizationRequest.xsd")
                        .setBody(constant("<ok/>"))
                    .handle(ValidationException.class)
                    .setBody(constant("<error/>"))
                    .end()
                    .to("log:out");
            }
        };
    }

}
