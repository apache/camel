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
package org.apache.camel.component.cxf.ssl;

import java.util.ArrayList;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.hamcrest.core.Is.is;

public class SslTest extends CamelSpringTestSupport {

    protected static final String GREET_ME_OPERATION = "greetMe";
    protected static final String TEST_MESSAGE = "Hello World!";
    protected static final String JAXWS_SERVER_ADDRESS
            = "https://localhost:" + CXFTestSupport.getPort1() + "/CxfSslTest/SoapContext/SoapPort";

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @AfterClass
    public static void cleanUp() {
        //System.clearProperty("cxf.config.file");
    }

    @BeforeClass
    public static void startService() {
        //System.getProperties().put("cxf.config.file", "/org/apache/camel/component/cxf/CxfSslContext.xml");
        //Greeter implementor = new GreeterImpl();
        //Endpoint.publish(JAXWS_SERVER_ADDRESS, implementor);
    }

    @Test
    public void testInvokingTrustRoute() throws Exception {
        Exchange reply = sendJaxWsMessage("direct:trust");
        assertFalse("We expect no exception here", reply.isFailed());
    }

    @Test
    public void testInvokingNoTrustRoute() throws Exception {
        Exchange reply = sendJaxWsMessage("direct:noTrust");
        assertTrue("We expect the exception here", reply.isFailed());
        Throwable e = reply.getException().getCause();
        assertThat(e.getClass().getCanonicalName(), is("javax.net.ssl.SSLHandshakeException"));
    }

    @Test
    public void testInvokingWrongTrustRoute() throws Exception {
        Exchange reply = sendJaxWsMessage("direct:wrongTrust");
        assertTrue("We expect the exception here", reply.isFailed());
        Throwable e = reply.getException().getCause();
        assertThat(e.getClass().getCanonicalName(), is("javax.net.ssl.SSLHandshakeException"));
    }

    protected Exchange sendJaxWsMessage(String endpointUri) throws InterruptedException {
        Exchange exchange = template.send(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
            }
        });
        return exchange;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        // we can put the http conduit configuration here
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfSslContext.xml");
    }

}