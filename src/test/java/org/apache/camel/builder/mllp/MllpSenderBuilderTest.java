/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder.mllp;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

@Ignore( value = "Not Yet Implemented")
public class MllpSenderBuilderTest extends CamelTestSupport {
    int listenPort = AvailablePortFinder.getNextAvailable();

    @EndpointInject( uri = "mock://mllp-receive")
    MockEndpoint receive;

    @EndpointInject( uri = "mock://mllp-journal")
    MockEndpoint journal;

    @EndpointInject( uri = "mock://mllp-nack-error")
    MockEndpoint nackError;

    @EndpointInject( uri = "mock://mllp-nack-reject")
    MockEndpoint nackReject;

    @EndpointInject( uri = "mock://mllp-error")
    MockEndpoint error;

    MllpSenderBuilder builder = new MllpSenderBuilder();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        builder.setTargetHostname( "0.0.0.0");
        builder.setTargetPort( listenPort );
        builder.setSourceEndpointUri( "direct://mllp-feed" );
        builder.setJournalEndpointUri( "mock://mllp-journal" );
        builder.setApplicationErrorEndpointUri( "mock://mllp-nack-error" );
        builder.setApplicationRejectEndpointUri( "mock://mllp-nack-reject" );
        builder.setErrorEndpointUri( "mock://mllp-error" );

        return builder;
    }

    @Test
    public void testMllpSender() throws Exception {
        receive.setExpectedMessageCount(1);
        journal.setExpectedMessageCount(1);
        nackError.setExpectedMessageCount(0);
        nackReject.setExpectedMessageCount(0);
        error.setExpectedMessageCount(0);

        template.setDefaultEndpointUri( "direct://mllp-feed");
        // TODO: Feed the message

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }
}
