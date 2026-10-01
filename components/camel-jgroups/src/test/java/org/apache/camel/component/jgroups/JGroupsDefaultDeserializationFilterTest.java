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
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jgroups.JChannel;
import org.jgroups.ObjectMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code deserializationFilter=*} opts out of the post-read class check: it accepts any type (here a
 * {@code java.net.URI}, which the shared Camel default allow-list would otherwise deny) and also satisfies the consumer
 * start-up guard, so the consumer starts without any other pre-read control.
 */
public class JGroupsDefaultDeserializationFilterTest extends CamelTestSupport {

    String noFilterCluster = "noFilterCluster";

    JChannel noFilterChannel;

    @BindToRegistry("filterExceptionHandler")
    CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();

    @EndpointInject("mock:nofilter")
    MockEndpoint noFilterMock;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // deserializationFilter=* opts out of the class check and accepts any type
                from("jgroups:" + noFilterCluster + "?deserializationFilter=*&exceptionHandler=#filterExceptionHandler")
                        .to(noFilterMock);
            }
        };
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();
        noFilterChannel = new JChannel();
        noFilterChannel.connect(noFilterCluster);
    }

    @Override
    public void doPostTearDown() {
        if (noFilterChannel != null) {
            noFilterChannel.close();
        }
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
