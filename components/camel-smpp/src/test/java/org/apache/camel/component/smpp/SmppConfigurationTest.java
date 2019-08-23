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
package org.apache.camel.component.smpp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppConfiguration</code>
 */
public class SmppConfigurationTest {

    private SmppConfiguration configuration;

    @Before
    public void setUp() {
        configuration = new SmppConfiguration();
        configuration.setServiceType("CMT");
    }

    @Test
    public void getterShouldReturnTheDefaultValues() {
        assertEquals("1717", configuration.getDestAddr());
        assertEquals(0x00, configuration.getDestAddrNpi());
        assertEquals(0x00, configuration.getDestAddrTon());
        assertEquals("", configuration.getAddressRange());
        assertEquals(new Integer(5000), configuration.getEnquireLinkTimer());
        assertEquals("localhost", configuration.getHost());
        assertEquals(null, configuration.getPassword());
        assertEquals(new Integer(2775), configuration.getPort());
        assertEquals(0x01, configuration.getPriorityFlag());
        assertEquals(0x00, configuration.getProtocolId());
        assertEquals(0x01, configuration.getRegisteredDelivery());
        assertEquals(0x00, configuration.getReplaceIfPresentFlag());
        assertEquals("CMT", configuration.getServiceType());
        assertEquals("1616", configuration.getSourceAddr());
        assertEquals(0x00, configuration.getSourceAddrNpi());
        assertEquals(0x00, configuration.getSourceAddrTon());
        assertEquals("smppclient", configuration.getSystemId());
        assertEquals("", configuration.getSystemType());
        assertEquals(new Integer(10000), configuration.getTransactionTimer());
        assertEquals("ISO-8859-1", configuration.getEncoding());
        assertEquals(0x00, configuration.getNumberingPlanIndicator());
        assertEquals(0x00, configuration.getTypeOfNumber());
        assertEquals(false, configuration.isUsingSSL());
        assertEquals(5000, configuration.getInitialReconnectDelay());
        assertEquals(5000, configuration.getReconnectDelay());
        assertEquals(null, configuration.getHttpProxyHost());
        assertEquals(new Integer(3128), configuration.getHttpProxyPort());
        assertEquals(null, configuration.getHttpProxyUsername());
        assertEquals(null, configuration.getHttpProxyPassword());
        assertEquals(null, configuration.getSessionStateListener());
    }

    @Test
    public void getterShouldReturnTheSetValues() {
        setNoneDefaultValues(configuration);

        assertEquals("1919", configuration.getDestAddr());
        assertEquals(0x08, configuration.getDestAddrNpi());
        assertEquals(0x02, configuration.getDestAddrTon());
        assertEquals(new Integer(5001), configuration.getEnquireLinkTimer());
        assertEquals("127.0.0.1", configuration.getHost());
        assertEquals("secret", configuration.getPassword());
        assertEquals(new Integer(2776), configuration.getPort());
        assertEquals(0x00, configuration.getPriorityFlag());
        assertEquals(0x01, configuration.getProtocolId());
        assertEquals(0x00, configuration.getRegisteredDelivery());
        assertEquals(0x01, configuration.getReplaceIfPresentFlag());
        assertEquals("XXX", configuration.getServiceType());
        assertEquals("1818", configuration.getSourceAddr());
        assertEquals(0x08, configuration.getSourceAddrNpi());
        assertEquals(0x02, configuration.getSourceAddrTon());
        assertEquals("client", configuration.getSystemId());
        assertEquals("xx", configuration.getSystemType());
        assertEquals(new Integer(10001), configuration.getTransactionTimer());
        assertEquals("UTF-8", configuration.getEncoding());
        assertEquals(0x08, configuration.getNumberingPlanIndicator());
        assertEquals(0x02, configuration.getTypeOfNumber());
        assertEquals(true, configuration.isUsingSSL());
        assertEquals(5001, configuration.getInitialReconnectDelay());
        assertEquals(5002, configuration.getReconnectDelay());
        assertEquals("127.0.0.1", configuration.getHttpProxyHost());
        assertEquals(new Integer(3129), configuration.getHttpProxyPort());
        assertEquals("user", configuration.getHttpProxyUsername());
        assertEquals("secret", configuration.getHttpProxyPassword());
        assertNotNull(configuration.getSessionStateListener());
        assertEquals("1", configuration.getProxyHeaders().get("X-Proxy-Header"));
    }

    @Test
    public void getterShouldReturnTheConfigureValuesFromURI() throws URISyntaxException {
        configuration.configureFromURI(new URI("smpp://client@127.0.0.1:2776"));

        assertEquals("127.0.0.1", configuration.getHost());
        assertEquals(new Integer(2776), configuration.getPort());
        assertEquals("client", configuration.getSystemId());
    }

    @Test
    public void hostPortAndSystemIdFromComponentConfigurationShouldBeUsedIfAbsentFromUri() throws URISyntaxException {
        configuration.setHost("host");
        configuration.setPort(123);
        configuration.setSystemId("systemId");

        configuration.configureFromURI(new URI("smpp://?password=pw"));

        assertEquals("host", configuration.getHost());
        assertEquals(new Integer(123), configuration.getPort());
        assertEquals("systemId", configuration.getSystemId());
    }

    @Test
    public void cloneShouldReturnAnEqualInstance() {
        setNoneDefaultValues(configuration);
        SmppConfiguration config = configuration.copy();

        assertEquals(config.getDestAddr(), configuration.getDestAddr());
        assertEquals(config.getDestAddrNpi(), configuration.getDestAddrNpi());
        assertEquals(config.getDestAddrTon(), configuration.getDestAddrTon());
        assertEquals(config.getEnquireLinkTimer(), configuration.getEnquireLinkTimer());
        assertEquals(config.getHost(), configuration.getHost());
        assertEquals(config.getPassword(), configuration.getPassword());
        assertEquals(config.getPort(), configuration.getPort());
        assertEquals(config.getPriorityFlag(), configuration.getPriorityFlag());
        assertEquals(config.getProtocolId(), configuration.getProtocolId());
        assertEquals(config.getRegisteredDelivery(), configuration.getRegisteredDelivery());
        assertEquals(config.getReplaceIfPresentFlag(), configuration.getReplaceIfPresentFlag());
        assertEquals(config.getServiceType(), configuration.getServiceType());
        assertEquals(config.getSourceAddr(), configuration.getSourceAddr());
        assertEquals(config.getSourceAddrNpi(), configuration.getSourceAddrNpi());
        assertEquals(config.getSourceAddrTon(), configuration.getSourceAddrTon());
        assertEquals(config.getSystemId(), configuration.getSystemId());
        assertEquals(config.getSystemType(), configuration.getSystemType());
        assertEquals(config.getTransactionTimer(), configuration.getTransactionTimer());
        assertEquals(config.getEncoding(), configuration.getEncoding());
        assertEquals(config.getNumberingPlanIndicator(), configuration.getNumberingPlanIndicator());
        assertEquals(config.getTypeOfNumber(), configuration.getTypeOfNumber());
        assertEquals(config.isUsingSSL(), configuration.isUsingSSL());
        assertEquals(config.getInitialReconnectDelay(), configuration.getInitialReconnectDelay());
        assertEquals(config.getReconnectDelay(), configuration.getReconnectDelay());
        assertEquals(config.getHttpProxyHost(), configuration.getHttpProxyHost());
        assertEquals(config.getHttpProxyPort(), configuration.getHttpProxyPort());
        assertEquals(config.getHttpProxyUsername(), configuration.getHttpProxyUsername());
        assertEquals(config.getHttpProxyPassword(), configuration.getHttpProxyPassword());
        assertEquals(config.getSessionStateListener(), configuration.getSessionStateListener());
        assertEquals(config.getProxyHeaders(), configuration.getProxyHeaders());
    }

    @Test
    public void toStringShouldListAllInstanceVariables() {
        String expected = "SmppConfiguration["
                + "usingSSL=false, "
                + "enquireLinkTimer=5000, "
                + "host=localhost, "
                + "password=null, "
                + "port=2775, "
                + "systemId=smppclient, "
                + "systemType=, "
                + "dataCoding=0, "
                + "alphabet=0, "
                + "encoding=ISO-8859-1, "
                + "transactionTimer=10000, "
                + "registeredDelivery=1, "
                + "serviceType=CMT, "
                + "sourceAddrTon=0, "
                + "destAddrTon=0, "
                + "sourceAddrNpi=0, "
                + "destAddrNpi=0, "
                + "addressRange=, "
                + "protocolId=0, "
                + "priorityFlag=1, "
                + "replaceIfPresentFlag=0, "
                + "sourceAddr=1616, "
                + "destAddr=1717, "
                + "typeOfNumber=0, "
                + "numberingPlanIndicator=0, "
                + "initialReconnectDelay=5000, "
                + "reconnectDelay=5000, "
                + "maxReconnect=2147483647, "
                + "lazySessionCreation=false, "
                + "httpProxyHost=null, "
                + "httpProxyPort=3128, "
                + "httpProxyUsername=null, "
                + "httpProxyPassword=null, "
                + "splittingPolicy=ALLOW, "
                + "proxyHeaders=null]";

        assertEquals(expected, configuration.toString());
    }

    private void setNoneDefaultValues(SmppConfiguration config) {
        config.setDestAddr("1919");
        config.setDestAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        config.setDestAddrTon(TypeOfNumber.NATIONAL.value());
        config.setEnquireLinkTimer(new Integer(5001));
        config.setHost("127.0.0.1");
        config.setPassword("secret");
        config.setPort(new Integer(2776));
        config.setPriorityFlag((byte) 0);
        config.setProtocolId((byte) 1);
        config.setRegisteredDelivery(SMSCDeliveryReceipt.DEFAULT.value());
        config.setReplaceIfPresentFlag((byte) 1);
        config.setServiceType("XXX");
        config.setSourceAddr("1818");
        config.setSourceAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        config.setSourceAddrTon(TypeOfNumber.NATIONAL.value());
        config.setSystemId("client");
        config.setSystemType("xx");
        config.setTransactionTimer(new Integer(10001));
        config.setEncoding("UTF-8");
        config.setNumberingPlanIndicator(NumberingPlanIndicator.NATIONAL.value());
        config.setTypeOfNumber(TypeOfNumber.NATIONAL.value());
        config.setUsingSSL(true);
        config.setInitialReconnectDelay(5001);
        config.setReconnectDelay(5002);
        config.setHttpProxyHost("127.0.0.1");
        config.setHttpProxyPort(new Integer(3129));
        config.setHttpProxyUsername("user");
        config.setHttpProxyPassword("secret");
        config.setSessionStateListener(new SessionStateListener() {
            public void onStateChange(SessionState arg0, SessionState arg1, Session arg2) {
            }
        });
        Map<String, String> proxyHeaders = new HashMap<>();
        proxyHeaders.put("X-Proxy-Header", "1");
        config.setProxyHeaders(proxyHeaders);
    }
}
