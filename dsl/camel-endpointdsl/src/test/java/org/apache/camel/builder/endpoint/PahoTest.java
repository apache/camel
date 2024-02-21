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

import org.apache.camel.Endpoint;
import org.apache.camel.builder.endpoint.dsl.PahoEndpointBuilderFactory;
import org.apache.camel.component.paho.PahoEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PahoTest extends BaseEndpointDslTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testPaho() throws Exception {
        context.start();

        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                PahoEndpointBuilderFactory.PahoEndpointBuilder builder
                        = paho("mytopic").brokerUrl("mybroker:1234").userName("myUser").password("myPassword");
                Endpoint endpoint = builder.resolve(context);
                assertNotNull(endpoint);
                PahoEndpoint pe = assertIsInstanceOf(PahoEndpoint.class, endpoint);
                assertEquals("mybroker:1234", pe.getConfiguration().getBrokerUrl());
                assertEquals("myUser", pe.getConfiguration().getUserName());
                assertEquals("myPassword", pe.getConfiguration().getPassword());
                assertEquals("mytopic", pe.getTopic());
            }
        });

        context.stop();
    }

}
