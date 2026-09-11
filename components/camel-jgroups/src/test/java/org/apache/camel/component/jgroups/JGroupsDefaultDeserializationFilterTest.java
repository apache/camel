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

import java.net.URI;

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
 * Verifies the default deserialization filter applied when the {@code deserializationFilter} option is not configured:
 * the shared Camel default allow-list denies {@code java.net.**}, and {@code deserializationFilter=*} opts out of the
 * check.
 */
public class JGroupsDefaultDeserializationFilterTest extends CamelTestSupport {

    String defaultFilterCluster = "defaultFilterCluster";

    String noFilterCluster = "noFilterCluster";

    JChannel defaultFilterChannel;

    JChannel noFilterChannel;

    @BindToRegistry("filterExceptionHandler")
    CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();

    @EndpointInject("mock:default")
    MockEndpoint defaultFilterMock;

    @EndpointInject("mock:nofilter")
    MockEndpoint noFilterMock;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no deserializationFilter configured, so the shared Camel default allow-list applies
                from("jgroups:" + defaultFilterCluster + "?exceptionHandler=#filterExceptionHandler")
                        .to(defaultFilterMock);
                // opt out of the class check
                from("jgroups:" + noFilterCluster + "?deserializationFilter=*").to(noFilterMock);
            }
        };
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        defaultFilterChannel = new JChannel();
        defaultFilterChannel.connect(defaultFilterCluster);
        noFilterChannel = new JChannel();
        noFilterChannel.connect(noFilterCluster);
    }

    @Override
    public void doPostTearDown() {
        if (defaultFilterChannel != null) {
            defaultFilterChannel.close();
        }
        if (noFilterChannel != null) {
            noFilterChannel.close();
        }
    }

    @Test
    public void shouldRejectDeniedTypeWithDefaultFilter() throws Exception {
        // java.net.** is denied by the default filter; the String sentinel sent afterwards is allowed and, thanks to
        // per-sender FIFO ordering, proves the URI was refused before reaching the route
        defaultFilterMock.setExpectedMessageCount(1);
        defaultFilterMock.expectedBodiesReceived("sentinel");

        defaultFilterChannel.send(new ObjectMessage(null, URI.create("http://localhost")));
        defaultFilterChannel.send(new ObjectMessage(null, "sentinel"));

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, exceptionHandler.getExceptions().size(), "The URI should have been refused");
        Throwable refused = exceptionHandler.getExceptions().get(0);
        assertInstanceOf(JGroupsException.class, refused);
        assertTrue(refused.getMessage().contains(URI.class.getName()),
                "Should report the refused type, but was: " + refused.getMessage());
    }

    @Test
    public void shouldAcceptAnyTypeWhenFilterIsDisabled() throws Exception {
        noFilterMock.setExpectedMessageCount(1);
        noFilterMock.expectedBodiesReceived(URI.create("http://localhost"));

        noFilterChannel.send(new ObjectMessage(null, URI.create("http://localhost")));

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(exceptionHandler.getExceptions().isEmpty(), "No message should have been refused");
    }

}
