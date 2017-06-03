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

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

public class MllpTcpClientProducerAcknowledgementTest extends CamelTestSupport {
    static final String TEST_MESSAGE =
        "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20161206193919|RISTECH|ADT^A08|00001|D|2.3^^|||||||" + '\r'
            + "EVN|A08|20150107161440||REG_UPDATE_SEND_VISIT_MESSAGES_ON_PATIENT_CHANGES|RISTECH^RADIOLOGY^TECHNOLOGIST^^^^^^UCLA^^^^^RRMC||" + '\r'
            + "PID|1|2100355^^^MRN^MRN|2100355^^^MRN^MRN||MDCLS9^MC9||19700109|F||U|111 HOVER STREET^^LOS ANGELES^CA^90032^USA^P^^LOS ANGELE|LOS ANGELE|"
                + "(310)725-6952^P^PH^^^310^7256952||ENGLISH|U||60000013647|565-33-2222|||U||||||||N||" + '\r'
            + "PD1|||UCLA HEALTH SYSTEM^^10|10002116^ADAMS^JOHN^D^^^^^EPIC^^^^PROVID||||||||||||||" + '\r'
            + "NK1|1|DOE^MC9^^|OTH|^^^^^USA|(310)888-9999^^^^^310^8889999|(310)999-2222^^^^^310^9992222|Emergency Contact 1|||||||||||||||||||||||||||" + '\r'
            + "PV1|1|OUTPATIENT|RR CT^^^1000^^^^^^^DEPID|EL|||017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID|017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID||||||"
                + "CLR|||||60000013647|SELF|||||||||||||||||||||HOV_CONF|^^^1000^^^^^^^||20150107161438||||||||||" + '\r'
            + "PV2||||||||20150107161438||||CT BRAIN W WO CONTRAST||||||||||N|||||||||||||||||||||||||||" + '\r'
            + "ZPV||||||||||||20150107161438|||||||||" + '\r'
            + "AL1|1||33361^NO KNOWN ALLERGIES^^NOTCOMPUTRITION^NO KNOWN ALLERGIES^EXTELG||||||" + '\r'
            + "DG1|1|DX|784.0^Headache^DX|Headache||VISIT" + '\r'
            + "GT1|1|1000235129|MDCLS9^MC9^^||111 HOVER STREET^^LOS ANGELES^CA^90032^USA^^^LOS ANGELE|(310)725-6952^^^^^310^7256952||19700109|F|P/F|SLF|"
                + "565-33-2222|||||^^^^^USA|||UNKNOWN|||||||||||||||||||||||||||||" + '\r'
            + "UB2||||||||" + '\r'
            + '\n';

    static final String EXPECTED_AA =
        "MSH|^~\\&|JCAPS|CC|ADT|EPIC|20161206193919|RISTECH|ACK^A08|00001|D|2.3^^|||||||" + '\r'
            + "MSA|AA|00001|" + '\r'
            + '\n';

    static final String EXPECTED_AR =
        "MSH|^~\\&|JCAPS|CC|ADT|EPIC|20161206193919|RISTECH|ACK^A08|00001|D|2.3^^|||||||" + '\r'
            + "MSA|AR|00001|" + '\r'
            + '\n';

    static final String EXPECTED_AE =
        "MSH|^~\\&|JCAPS|CC|ADT|EPIC|20161206193919|RISTECH|ACK^A08|00001|D|2.3^^|||||||" + '\r'
            + "MSA|AE|00001|" + '\r'
            + '\n';

    @Rule
    public MllpServerResource mllpServer = new MllpServerResource("localhost", AvailablePortFinder.getNextAvailable());

    @EndpointInject(uri = "direct://source")
    ProducerTemplate source;

    @EndpointInject(uri = "mock://failed")
    MockEndpoint failed;

    @EndpointInject(uri = "mock://aa-ack")
    MockEndpoint aa;
    @EndpointInject(uri = "mock://ae-nack")
    MockEndpoint ae;
    @EndpointInject(uri = "mock://ar-nack")
    MockEndpoint ar;

    @EndpointInject(uri = "mock://invalid-ack")
    MockEndpoint invalid;

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

            public void configure() {
                onException(MllpApplicationRejectAcknowledgementException.class)
                        .handled(true)
                        .to(ar)
                        .log(LoggingLevel.ERROR, routeId, "AR Acknowledgement");

                onException(MllpApplicationErrorAcknowledgementException.class)
                        .handled(true)
                        .to(ae)
                        .log(LoggingLevel.ERROR, routeId, "AE Acknowledgement");

                onException(MllpInvalidAcknowledgementException.class)
                        .handled(true)
                        .to(invalid)
                        .log(LoggingLevel.ERROR, routeId, "Invalid Acknowledgement");

                onCompletion()
                        .onFailureOnly()
                        .to(failed)
                        .log(LoggingLevel.DEBUG, routeId, "Exchange failed");

                from(source.getDefaultEndpoint()).routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Sending Message")
                        .toF("mllp://%s:%d", mllpServer.getListenHost(), mllpServer.getListenPort())
                        .log(LoggingLevel.INFO, routeId, "Received Acknowledgement")
                        .to(aa);
            }
        };
    }

    @Test
    public void testApplicationAcceptAcknowledgement() throws Exception {
        aa.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, EXPECTED_AA.getBytes());
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, EXPECTED_AA);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        ae.expectedMessageCount(0);
        ar.expectedMessageCount(0);
        invalid.expectedMessageCount(0);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationRejectAcknowledgement() throws Exception {
        ar.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AR");
        ar.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, EXPECTED_AR.getBytes());
        ar.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, EXPECTED_AR);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        aa.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        invalid.expectedMessageCount(0);

        mllpServer.setSendApplicationRejectAcknowledgementModulus(1);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationErrorAcknowledgement() throws Exception {
        ae.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AE");
        ae.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, EXPECTED_AE.getBytes());
        ae.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, EXPECTED_AE);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        aa.expectedMessageCount(0);
        ar.expectedMessageCount(0);
        invalid.expectedMessageCount(0);

        mllpServer.setSendApplicationErrorAcknowledgementModulus(1);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testEmptyAcknowledgement() throws Exception {
        aa.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "");
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, "".getBytes());
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, "");

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        ar.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        invalid.expectedMessageCount(0);

        mllpServer.setExcludeAcknowledgementModulus(1);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidAcknowledgement() throws Exception {
        final String badAcknowledgement = "A VERY BAD ACKNOWLEDGEMENT";

        aa.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "");
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement.getBytes());
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        ar.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        invalid.expectedMessageCount(0);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidAcknowledgementContainingEmbeddedStartOfBlock() throws Exception {
        final String badAcknowledgement = EXPECTED_AA.replaceFirst("RISTECH", "RISTECH" + START_OF_BLOCK);

        aa.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement.getBytes());
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        ar.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        invalid.expectedMessageCount(0);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidAcknowledgementContainingEmbeddedEndOfBlock() throws Exception {
        final String badAcknowledgement = EXPECTED_AA.replaceFirst("RISTECH", "RISTECH" + END_OF_BLOCK);

        aa.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement.getBytes());
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        ar.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        invalid.expectedMessageCount(0);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }
}
