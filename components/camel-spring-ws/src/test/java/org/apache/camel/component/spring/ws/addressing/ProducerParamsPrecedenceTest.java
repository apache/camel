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
package org.apache.camel.component.spring.ws.addressing;

import java.net.URI;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.spring.ws.utils.OutputChannelReceiver;
import org.apache.camel.component.spring.ws.utils.TestUtil;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.fest.assertions.Assertions;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;

public class ProducerParamsPrecedenceTest extends CamelSpringTestSupport {

    private static URI anonymousUri;

    private final String xmlBody = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    private OutputChannelReceiver sender;

    @Produce
    private ProducerTemplate template;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        sender = getMandatoryBean(OutputChannelReceiver.class, "senderReceiver");
        sender.clear();
    }

    @BeforeClass
    public static void setUpConstants() throws Exception {
        anonymousUri = new URI("http://www.w3.org/2005/08/addressing/anonymous");
    }

    // TODO AZ
    @Test
    @Ignore
    public void testWsAddressingAction() throws Exception {
        Object result = template.requestBody("direct:wsAddressingAction", xmlBody);
        assertNotNull(result);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo()).isNull();
        Assertions.assertThat(wsaProperties.getFaultTo()).isNull();

    }

    // TODO AZ
    @Test
    @Ignore
    public void testWsAddressingActionPrecendence() throws Exception {
        Object result = template.requestBody("direct:precedenceWsAddressingAction", xmlBody);
        assertNotNull(result);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo()).isNull();
        Assertions.assertThat(wsaProperties.getFaultTo()).isNull();

    }

    @Test
    public void testReplyTo() throws Exception {
        template.requestBody("direct:replyTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(new URI("http://reply.to"));
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://fault.to"));

    }

    @Test
    public void testReplyToPrecedence() throws Exception {
        template.requestBody("direct:precedenceReplyTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(new URI("http://replyPrecedence.to"));
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://faultPrecedence.to"));

    }

    @Test
    public void testFaultTo() throws Exception {
        template.requestBody("direct:faultTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://fault.to"));
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(anonymousUri);

    }

    @Test
    public void testFaultToPrecedence() throws Exception {
        template.requestBody("direct:precedenceFaultTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://faultPrecedence.to"));
        // /we set in sample data all precendence fields for simplier tests
        // otherwise it woudl be here annonymous
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(new URI("http://replyPrecedence.to"));

    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"org/apache/camel/component/spring/ws/addresing/ProducerParamsPrecedenceTest-context.xml"});
    }
}
