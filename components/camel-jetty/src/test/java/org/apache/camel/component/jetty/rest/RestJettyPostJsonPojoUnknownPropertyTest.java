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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * REST binding rejects unknown properties with Jackson 3 too, whose default is to accept them.
 */
public class RestJettyPostJsonPojoUnknownPropertyTest extends BaseJettyTest {

    @Test
    public void testUnknownPropertyIsRejected() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(0);

        String body = "{\"id\": 123, \"name\": \"Donald Duck\", \"donotexists\": \"foo\"}";
        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("http://localhost:" + getPort() + "/users/new", body));

        assertIsInstanceOf(HttpOperationFailedException.class, ex.getCause());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testKnownPropertiesAreAccepted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserPojo.class);

        template.sendBody("http://localhost:" + getPort() + "/users/new", "{\"id\": 123, \"name\": \"Donald Duck\"}");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                configureDataFormat(
                        restConfiguration().component("jetty").host("localhost").port(getPort())
                                .bindingMode(RestBindingMode.json));

                rest("/users/").post("new").type(UserPojo.class).to("mock:input");
            }
        };
    }

    protected void configureDataFormat(RestConfigurationDefinition rest) {
        // the defaults of REST binding
    }
}
