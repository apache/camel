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
package org.apache.camel.component.mllp;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@Disabled("This test hangs")
public class MllpTcpServerConsumerTransactionTest extends CamelTestSupport {

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createVMService();

    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @EndpointInject("mock://on-complete-only")
    MockEndpoint complete;

    @EndpointInject("mock://on-failure-only")
    MockEndpoint failure;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        return context;
    }

    @BindToRegistry("target")
    public SjmsComponent addTargetComponent() {

        SjmsComponent target = new SjmsComponent();
        target.setConnectionFactory(new ActiveMQConnectionFactory(service.serviceAddress()));

        return target;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            int connectTimeout = 500;
            int responseTimeout = 5000;

            @Override
            public void configure() {
                String routeId = "mllp-test-receiver-route";

                onCompletion()
                        .onCompleteOnly()
                        .log(LoggingLevel.INFO, routeId, "Test route complete")
                        .to(complete);

                onCompletion()
                        .onFailureOnly()
                        .log(LoggingLevel.INFO, routeId, "Test route failed")
                        .to(failure);

                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&receiveTimeout=%d",
                        mllpClient.getMllpHost(), mllpClient.getMllpPort(), connectTimeout, responseTimeout)
                        .routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Test route received message")
                        .to("target://test-queue?transacted=true");

                from("target://test-queue")
                        .routeId("jms-consumer")
                        .process(exchange -> System.out.println(exchange.getIn().getBody()))
                        .log(LoggingLevel.INFO, routeId, "Test JMS Consumer received message")
                        .to(result);

            }
        };
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        result.expectedMessageCount(1);
        complete.expectedMessageCount(2);
        failure.expectedMessageCount(0);

        mllpClient.connect();

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(), 10000);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    public void testAcknowledgementWriteFailure() throws Exception {
        result.expectedMessageCount(1);
        result.setAssertPeriod(1000);
        complete.expectedMessageCount(1);
        failure.expectedMessageCount(1);

        mllpClient.connect();
        mllpClient.setDisconnectMethod(MllpClientResource.DisconnectMethod.RESET);

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage(), true);

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }
}
