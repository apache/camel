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
package org.apache.camel.component.mllp;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class MllpTcpClientProducerTest extends CamelTestSupport {
    @Rule
    public MllpServerResource mllpServer = new MllpServerResource( AvailablePortFinder.getNextAvailable() );

    String targetURI = "direct://mllp-sender";
    int deliveryDelay = 25;

    @EndpointInject(uri = "mock://response")
    MockEndpoint response;

    @EndpointInject(uri = "mock://timeout-ex")
    MockEndpoint timeout;

    @EndpointInject(uri = "mock://frame-ex")
    MockEndpoint frame;

    @Override
    public String isMockEndpoints() {
        return "log://netty-mllp-sender-throughput*";
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        return registry;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            int connectTimeout = 1000;
            int responseTimeout = 1000;

            @Override
            public void configure() throws Exception {
                errorHandler(
                        defaultErrorHandler()
                                .allowRedeliveryWhileStopping(false)
                );
                /*
                onException(ReadTimeoutException.class, ConnectException.class)
                        .redeliveryDelay(2500)
//                .maximumRedeliveryDelay(15000)
//                .backOffMultiplier(2)
                        .maximumRedeliveries(-1)
                        .logContinued(true)
                        .logExhausted(true)
                        .logExhaustedMessageHistory(false)
                        .logHandled(true)
                        .logRetryAttempted(true)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .toF("log:%s-connect-ex?level=WARN", routeId)
                ;
                */

                /*
                onException(ReadTimeoutException.class)
                        .logHandled(true)
                        .handled(true)
                        .to("direct://handle-timeout")
                ;
                */

                onException(MllpFrameException.class)
                        .handled(true)
                        .logHandled(false)
                        .to("mock://frame-ex")
                ;

                onException(MllpTimeoutException.class)
                        .handled(true)
                        .logHandled(false)
                        .to("mock://timeout-ex")
                ;

                onCompletion().onFailureOnly().log(LoggingLevel.ERROR, "Processing Failed");

                from(targetURI)
                        .routeId("mllp-sender-test-route")
                        .log(LoggingLevel.INFO, "Sending Message: $simple{header[CamelHL7MessageControl]}")
                        .toF("mllp://%s:%d?connectTimeout=%d&responseTimeout=%d",
                                "0.0.0.0", mllpServer.getListenPort(), connectTimeout, responseTimeout)
                        .to("mock://response")
                ;

                from("direct://handle-timeout")
                        .log(LoggingLevel.ERROR, "Response Timeout")
                        .rollback()
                ;

            }
        };

    }

    @Test
    public void testSendSingleMessage() throws Exception {
        response.setExpectedMessageCount(1);
        timeout.setExpectedMessageCount(0);
        frame.setExpectedMessageCount(0);

        template.sendBody(targetURI, Data.TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }


    @Test
    public void testSendMultipleMessages() throws Exception {
        int messageCount = 5;
        response.setExpectedMessageCount(messageCount);
        timeout.setExpectedMessageCount(0);
        frame.setExpectedMessageCount(0);

        for (int i = 0; i < messageCount; ++i) {
            template.sendBody(targetURI, Data.TEST_MESSAGE);
            Thread.sleep(deliveryDelay);
        }

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }


    @Test
    public void testNoResponseOnFirstMessage() throws Exception {
        int sendMessageCount = 5;
        response.setExpectedMessageCount(sendMessageCount - 1);
        timeout.expectedMessageCount(1);
        frame.setExpectedMessageCount(0);

        NotifyBuilder notify1 = new NotifyBuilder(context).whenDone(1).create();
        NotifyBuilder notify2 = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.disableResponse();

        template.setDefaultEndpointUri(targetURI);
        template.sendBody(Data.TEST_MESSAGE);
        assertTrue("Notify not completed", notify1.matches(10, TimeUnit.SECONDS));
        mllpServer.enableResponse();

        for (int i = 2; i <= sendMessageCount; ++i) {
            template.sendBody(Data.TEST_MESSAGE);
            Thread.sleep(deliveryDelay);
        }
        assertTrue("Notify not completed", notify2.matches(10, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testNoResponseOnNthMessage() throws Exception {
        int sendMessageCount = 3;
        response.setExpectedMessageCount(sendMessageCount - 1);
        timeout.expectedMessageCount(1);
        frame.setExpectedMessageCount(0);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.disableResponse(sendMessageCount);

        template.setDefaultEndpointUri(targetURI);
        for (int i = 0; i < sendMessageCount; ++i) {
            template.sendBody(Data.TEST_MESSAGE);
            Thread.sleep(deliveryDelay);
        }

        assertTrue("Notify not completed", notify.matches(sendMessageCount, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testMissingEndOfDataByte() throws Exception {
        int sendMessageCount = 3;
        response.setExpectedMessageCount(sendMessageCount - 1);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.setExcludeEndOfDataModulus(sendMessageCount);

        template.setDefaultEndpointUri(targetURI);
        for (int i = 0; i < sendMessageCount; ++i) {
            template.sendBody(Data.TEST_MESSAGE);
            Thread.sleep(deliveryDelay);
        }

        assertTrue("Notify not completed", notify.matches(sendMessageCount, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testMissingEndOfBlockByte() throws Exception {
        int sendMessageCount = 3;
        response.setExpectedMessageCount(sendMessageCount - 1);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.setExcludeEndOfBlockModulus(sendMessageCount);

        template.setDefaultEndpointUri(targetURI);
        for (int i = 0; i < sendMessageCount; ++i) {
            template.sendBody(Data.TEST_MESSAGE);
            Thread.sleep(deliveryDelay);
        }

        assertTrue("Notify not completed", notify.matches(sendMessageCount, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationAcceptAcknowledgement() throws Exception {
        int messageCount = 5;
        response.setExpectedMessageCount(messageCount);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(messageCount).create();

        template.setDefaultEndpointUri(targetURI);
        for (int i = 0; i < messageCount; ++i) {
            template.sendBody(Data.TEST_MESSAGE);
            Thread.sleep(deliveryDelay);
        }

        assertTrue("Notify not completed", notify.matches(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    static class Data {
        static final String TEST_MESSAGE =
                "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20150107161440|RISTECH|ADT^A08|10001|D|2.3^^|||||||" + '\r' +
                        "EVN|A08|20150107161440||REG_UPDATE_SEND_VISIT_MESSAGES_ON_PATIENT_CHANGES|RISTECH^RADIOLOGY^TECHNOLOGIST^^^^^^UCLA^^^^^RRMC||" + '\r' +
                        "PID|1|2100355^^^MRN^MRN|2100355^^^MRN^MRN||MDCLS9^MC9||19700109|F||U|111 HOVER STREET^^LOS ANGELES^CA^90032^USA^P^^LOS ANGELE|LOS ANGELE|(310)725-6952^P^PH^^^310^7256952||ENGLISH|U||60000013647|565-33-2222|||U||||||||N||" + '\r' +
                        "PD1|||UCLA HEALTH SYSTEM^^10|10002116^ADAMS^JOHN^D^^^^^EPIC^^^^PROVID||||||||||||||" + '\r' +
                        "NK1|1|DOE^MC9^^|OTH|^^^^^USA|(310)888-9999^^^^^310^8889999|(310)999-2222^^^^^310^9992222|Emergency Contact 1|||||||||||||||||||||||||||" + '\r' +
                        "PV1|1|OUTPATIENT|RR CT^^^1000^^^^^^^DEPID|EL|||017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID|017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID||||||CLR|||||60000013647|SELF|||||||||||||||||||||HOV_CONF|^^^1000^^^^^^^||20150107161438||||||||||" + '\r' +
                        "PV2||||||||20150107161438||||CT BRAIN W WO CONTRAST||||||||||N|||||||||||||||||||||||||||" + '\r' +
                        "ZPV||||||||||||20150107161438|||||||||" + '\r' +
                        "AL1|1||33361^NO KNOWN ALLERGIES^^NOTCOMPUTRITION^NO KNOWN ALLERGIES^EXTELG||||||" + '\r' +
                        "DG1|1|DX|784.0^Headache^DX|Headache||VISIT" + '\r' +
                        "GT1|1|1000235129|MDCLS9^MC9^^||111 HOVER STREET^^LOS ANGELES^CA^90032^USA^^^LOS ANGELE|(310)725-6952^^^^^310^7256952||19700109|F|P/F|SLF|565-33-2222|||||^^^^^USA|||UNKNOWN|||||||||||||||||||||||||||||" + '\r' +
                        "UB2||||||||" + '\r' +
                        '\r' + '\n';
    }
}