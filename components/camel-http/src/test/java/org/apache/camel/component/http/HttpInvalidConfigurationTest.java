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
package org.apache.camel.component.http;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.component.http.HttpMethods.HTTP_METHOD;
import static org.apache.camel.component.http.HttpMethods.POST;

/**
 * Unit test of invalid configuraiton
 */
public class HttpInvalidConfigurationTest extends ContextTestSupport {

    protected void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should have thrown ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getMessage().endsWith("You have duplicated the http(s) protocol."));
        }
    }

    public void testInvalidHostConfiguratiob() {
        // dummy
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setHeader(HTTP_METHOD, POST).to("http://http://www.google.com");
            }
        };
    }

}
