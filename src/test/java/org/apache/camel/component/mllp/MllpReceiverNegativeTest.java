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

import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceException;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class MllpReceiverNegativeTest extends CamelTestSupport {
    MllpClientResource mllpClient;

    int mllpPort = AvailablePortFinder.getNextAvailable();
    String mllpHost = "localhost";

    @EndpointInject(uri = "mock://request")
    MockEndpoint request;

    int connectTimeout = 500;
    int responseTimeout = 5000;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mllpClient = new MllpClientResource(mllpPort);

        mllpClient.connect();
    }

    @Override
    public void tearDown() throws Exception {
        mllpClient.disconnect();

        super.tearDown();
    }

    @Override
    public String isMockEndpoints() {
        return "log:*";
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
            @Override
            public void configure() throws Exception {
                String routeId = "mllp-test-receiver-route";

                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&responseTimeout=%d",
                        mllpHost, mllpPort, connectTimeout, responseTimeout)
                        .routeId( routeId)
                        .log(LoggingLevel.INFO, routeId, "Receiving Message: $simple{header[CamelHL7MessageControl]}")
                        .to("mock://request")
                ;

            }
        };
    }

    @Test(timeout = 15000)
    public void testOpenMllpEnvelopeWithReset() throws Exception {
        request.expectedMessageCount(4);

        mllpClient.sendMessage(TEST_MESSAGE_1);
        String acknowledgement1 = mllpClient.receiveAcknowledgement();

        mllpClient.sendMessage(TEST_MESSAGE_2);
        String acknowledgement2 = mllpClient.receiveAcknowledgement();

        mllpClient.setSendEndOfBlock(false);
        mllpClient.setSendEndOfData(false);
        mllpClient.sendMessage(TEST_MESSAGE_3);
        // Acknowledgement won't come here
        try {
            mllpClient.receiveAcknowledgement();
        } catch (MllpJUnitResourceException timeoutEx ) {
            log.info( "Expected Timeout reading response");
        }
        mllpClient.disconnect();
        Thread.sleep(1000);
        mllpClient.connect();

        mllpClient.setSendEndOfBlock(true);
        mllpClient.setSendEndOfData(true);
        mllpClient.sendMessage(TEST_MESSAGE_4);
        String acknowledgement4 = mllpClient.receiveAcknowledgement();

        mllpClient.sendMessage(TEST_MESSAGE_5);
        String acknowledgement5 = mllpClient.receiveAcknowledgement();

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        assertTrue("Should be acknowledgment for message 1", acknowledgement1.contains("MSA|AA|10001"));
        assertTrue("Should be acknowledgment for message 2", acknowledgement2.contains("MSA|AA|10002"));
        // assertTrue("Should be acknowledgment for message 3", acknowledgement3.contains("MSA|AA|10003"));
        assertTrue("Should be acknowledgment for message 4", acknowledgement4.contains("MSA|AA|10004"));
        assertTrue("Should be acknowledgment for message 5", acknowledgement5.contains("MSA|AA|10005"));
    }

    static final String TEST_MESSAGE_1 =
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
    static final String TEST_MESSAGE_2 =
            "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20150107161440|RISTECH|ADT^A08|10002|D|2.3^^|||||||" + '\r' +
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
    static final String TEST_MESSAGE_3 =
            "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20150107161440|RISTECH|ADT^A08|10003|D|2.3^^|||||||" + '\r' +
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
    static final String TEST_MESSAGE_4 =
            "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20150107161440|RISTECH|ADT^A08|10004|D|2.3^^|||||||" + '\r' +
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
    static final String TEST_MESSAGE_5 =
            "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20150107161440|RISTECH|ADT^A08|10005|D|2.3^^|||||||" + '\r' +
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