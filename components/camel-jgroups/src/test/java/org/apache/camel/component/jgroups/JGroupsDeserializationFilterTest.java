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
package org.apache.camel.component.jgroups;

import java.util.Date;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.jgroups.JChannel;
import org.jgroups.ObjectMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the consumer {@code deserializationFilter} option constrains the message body types accepted from the
 * cluster: a type on the allow-list is routed, a type outside it is refused and reported to the consumer's exception
 * handler.
 */
public class JGroupsDeserializationFilterTest extends CamelTestSupport {

    String clusterName = "deserializationFilterCluster";

    JChannel channel;

    @BindToRegistry("filterExceptionHandler")
    CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();

    @EndpointInject("mock:test")
    MockEndpoint mockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // allow only String bodies, reject everything else
                from("jgroups:" + clusterName
                     + "?deserializationFilter=java.lang.String;!*&exceptionHandler=#filterExceptionHandler")
                        .to(mockEndpoint);
            }
        };
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        channel = new JChannel();
        channel.connect(clusterName);
    }

    @Override
    public void doPostTearDown() {
        if (channel != null) {
            channel.close();
        }
    }

    @Test
    public void shouldAcceptBodyAllowedByFilter() throws Exception {
        mockEndpoint.setExpectedMessageCount(1);
        mockEndpoint.expectedBodiesReceived("allowed");

        channel.send(new ObjectMessage(null, "allowed"));

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(exceptionHandler.getExceptions().isEmpty(), "No message should have been refused");
    }

    @Test
    public void shouldRejectBodyDeniedByFilter() throws Exception {
        // The Date sent first is not on the allow-list and must be dropped by the filter; the String sentinel sent
        // afterwards is allowed. JGroups preserves per-sender FIFO ordering, so receiving only the sentinel proves the
        // Date was refused before reaching the route.
        mockEndpoint.setExpectedMessageCount(1);
        mockEndpoint.expectedBodiesReceived("sentinel");

        channel.send(new ObjectMessage(null, new Date()));
        channel.send(new ObjectMessage(null, "sentinel"));

        MockEndpoint.assertIsSatisfied(context);

        // the refused message is not silently dropped, but reported to the consumer's exception handler. The
        // sentinel was sent after the Date and has already been routed, so the refusal has happened by now.
        assertEquals(1, exceptionHandler.getExceptions().size(), "The Date should have been refused");
        Throwable refused = exceptionHandler.getExceptions().get(0);
        assertInstanceOf(JGroupsException.class, refused);
        assertTrue(refused.getMessage().contains(Date.class.getName()),
                "Should report the refused type, but was: " + refused.getMessage());
    }

}
