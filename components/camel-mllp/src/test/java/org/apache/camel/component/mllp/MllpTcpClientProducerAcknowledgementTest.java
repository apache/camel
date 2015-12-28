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
package org.apache.camel.component.mllp;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.camel.test.mllp.Hl7MessageGenerator.generateMessage;


public class MllpTcpClientProducerAcknowledgementTest extends CamelTestSupport {
    @Rule
    public MllpServerResource mllpServer = new MllpServerResource(AvailablePortFinder.getNextAvailable());

    @EndpointInject(uri = "direct://source")
    ProducerTemplate source;

    @EndpointInject(uri = "mock://complete")
    MockEndpoint complete;

    @EndpointInject(uri = "mock://aa-ack")
    MockEndpoint accept;
    @EndpointInject(uri = "mock://ae-nack")
    MockEndpoint error;
    @EndpointInject(uri = "mock://ar-nack")
    MockEndpoint reject;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            String routeId = "mllp-sender";

            String host = "0.0.0.0";
            int port = mllpServer.getListenPort();

            public void configure() {
                onException(MllpApplicationRejectAcknowledgementException.class)
                        .handled(true)
                        .to(reject)
                        .log(LoggingLevel.ERROR, routeId, "AR Acknowledgemnet");

                onException(MllpApplicationErrorAcknowledgementException.class)
                        .handled(true)
                        .to(error)
                        .log(LoggingLevel.ERROR, routeId, "AE Acknowledgement");

                onCompletion()
                        .onCompleteOnly()
                        .to(complete)
                        .log(LoggingLevel.DEBUG, routeId, "AA Acknowledgement");

                from(source.getDefaultEndpoint()).routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Sending Message")
                        .toF("mllp://%s:%d", host, port)
                        .log(LoggingLevel.INFO, routeId, "Received Acknowledgement")
                        .to(accept);
            }
        };
    }

    @Test
    public void testApplicationAcceptAcknowledgement() throws Exception {
        complete.setExpectedMessageCount(1);
        accept.setExpectedMessageCount(1);
        reject.setExpectedMessageCount(0);
        error.setExpectedMessageCount(0);

        source.sendBody(generateMessage());

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationRejectAcknowledgement() throws Exception {
        complete.setExpectedMessageCount(1);
        accept.setExpectedMessageCount(0);
        reject.setExpectedMessageCount(1);
        error.setExpectedMessageCount(0);

        mllpServer.setSendApplicationRejectAcknowledgementModulus(1);

        source.sendBody(generateMessage());

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationErrorAcknowledgement() throws Exception {
        complete.setExpectedMessageCount(1);
        accept.setExpectedMessageCount(0);
        reject.setExpectedMessageCount(0);
        error.setExpectedMessageCount(1);

        mllpServer.setSendApplicationErrorAcknowledgementModulus(1);

        source.sendBody(generateMessage());

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

}
