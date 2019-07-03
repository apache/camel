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

public class MllpTcpClientProducerAcknowledgementValidationTest extends CamelTestSupport {
    static final String TEST_MESSAGE =
        "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||" + '\r'
            + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||" + '\r'
            + "NTE|1||Free text for entering clinical details|" + '\r'
            + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + '\r'
            + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||" + '\r'
            + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + '\n';

    static final String EXPECTED_AA =
        "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001|D|2.3|||||||" + '\r'
            + "MSA|AA|00001|" + '\r'
            + '\n';

    static final String EXPECTED_AR =
        "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001|D|2.3|||||||" + '\r'
            + "MSA|AR|00001|" + '\r'
            + '\n';

    static final String EXPECTED_AE =
        "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001|D|2.3|||||||" + '\r'
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
                        .toF("mllp://%s:%d?validatePayload=true", mllpServer.getListenHost(), mllpServer.getListenPort())
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
        invalid.expectedBodiesReceived(TEST_MESSAGE);
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, "".getBytes());
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, "");

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        aa.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        ar.expectedMessageCount(0);

        mllpServer.setExcludeAcknowledgementModulus(1);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidAcknowledgement() throws Exception {
        final String badAcknowledgement = "A VERY BAD ACKNOWLEDGEMENT";

        invalid.expectedBodiesReceived(TEST_MESSAGE);
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement.getBytes());
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        aa.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        ar.expectedMessageCount(0);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidAcknowledgementContainingEmbeddedStartOfBlock() throws Exception {
        final String badAcknowledgement = EXPECTED_AA.replaceFirst("|ORM", START_OF_BLOCK + "|ORM" );

        invalid.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement.getBytes());
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        aa.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        ar.expectedMessageCount(0);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testInvalidAcknowledgementContainingEmbeddedEndOfBlock() throws Exception {
        final String badAcknowledgement = EXPECTED_AA.replaceFirst("|ORM", END_OF_BLOCK + "|ORM" );

        invalid.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement.getBytes());
        invalid.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement);

        failed.expectedMessageCount(0);
        failed.setAssertPeriod(1000);

        aa.expectedMessageCount(0);
        ae.expectedMessageCount(0);
        ar.expectedMessageCount(0);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }
}
