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
package org.apache.camel.component.http;

import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.POST;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test of invalid configuration
 */
public class HttpInvalidHttpClientConfigurationTest extends CamelTestSupport {

    private FailedToCreateRouteException exception;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should have thrown ResolveEndpointFailedException");
        } catch (FailedToCreateRouteException e) {
            exception = e;
        }
    }

    @Test
    public void testInvalidHostConfiguration() {
        ResolveEndpointFailedException cause = assertIsInstanceOf(ResolveEndpointFailedException.class, exception.getCause());
        assertTrue(cause.getMessage().endsWith("Unknown parameters=[{xxx=true}]"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setHeader(Exchange.HTTP_METHOD, POST).to("http://www.google.com?httpClient.xxx=true");
            }
        };
    }
}
