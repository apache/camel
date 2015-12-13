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

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class MllpTcpClientProducerAcknowledgementTest extends CamelTestSupport {
    @Rule
    public MllpServerResource mllpServer = new MllpServerResource(AvailablePortFinder.getNextAvailable());
    @EndpointInject(uri = "mock://complete")
    MockEndpoint complete;
    @EndpointInject(uri = "mock://aa-ack")
    MockEndpoint accept;
    @EndpointInject(uri = "mock://ae-nack")
    MockEndpoint error;
    @EndpointInject(uri = "mock://ar-nack")
    MockEndpoint reject;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            String routeId = "mllp-sender";

            String host = "0.0.0.0";
            int port = mllpServer.getListenPort();

            public void configure() {
                onException(MllpApplicationRejectAcknowledgementException.class)
                        .handled(true)
                        .to("mock://ar-nack")
                        .log(LoggingLevel.ERROR, routeId, "AR Acknowledgemnet")
                ;
                onException(MllpApplicationErrorAcknowledgementException.class)
                        .handled(true)
                        .to("mock://ae-nack")
                        .log(LoggingLevel.ERROR, routeId, "AE Acknowledgemnet")
                ;
                onCompletion()
                        .onCompleteOnly()
                        .to("mock://complete")
                        .log(LoggingLevel.ERROR, routeId, "AA Acknowledgemnet")
                ;

                fromF("direct://trigger").routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Sending Message")
                        .toF("mllp://%s:%d", host, port)
                        .log(LoggingLevel.INFO, routeId, "Received Acknowledgement")
                        .to( "mock://aa-ack")
                ;
            }
        };
    }

    @Test
    public void testApplicationAcceptAcknowledgement() throws Exception {
        complete.setExpectedMessageCount(1);
        accept.setExpectedMessageCount(1);
        reject.setExpectedMessageCount(0);
        error.setExpectedMessageCount(0);

        template.sendBody("direct://trigger", Data.TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationRejectAcknowledgement() throws Exception {
        complete.setExpectedMessageCount(1);
        accept.setExpectedMessageCount(0);
        reject.setExpectedMessageCount(1);
        error.setExpectedMessageCount(0);

        mllpServer.setSendApplicationRejectAcknowledgementModulus( 1 );

        template.sendBody("direct://trigger", Data.TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationErrorAcknowledgement() throws Exception {
        complete.setExpectedMessageCount(1);
        accept.setExpectedMessageCount(0);
        reject.setExpectedMessageCount(0);
        error.setExpectedMessageCount(1);

        mllpServer.setSendApplicationErrorAcknowledgementModulus( 1 );

        template.sendBody("direct://trigger", Data.TEST_MESSAGE);

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
