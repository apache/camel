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
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;

public class ProducerParamsBasicTest extends CamelSpringTestSupport {

    private static URI anonymousUri;

    private final String xmlBody = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    private OutputChannelReceiver sender;

    @Produce
    private ProducerTemplate template;

    @Override
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

    @Test
    public void testDefaultReplyTo() throws Exception {
        template.requestBody("direct:defaultOk", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(anonymousUri);
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(anonymousUri);

    }

    @Test
    public void testDefaulFaultTo() throws Exception {
        template.requestBody("direct:defaultFault", xmlBody);
        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(anonymousUri);
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(anonymousUri);

    }

    @Test
    public void testReplyTo() throws Exception {
        template.requestBody("direct:replyTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(new URI("mailto://replyTo@chocolatejar.eu"));
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://fault.to"));

    }

    @Test
    public void testFaultTo() throws Exception {
        template.requestBody("direct:faultTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(anonymousUri);
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://fault.to"));

    }

    @Test
    public void testFaultFollowsReply() throws Exception {
        template.requestBody("direct:omittedFaultTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(new URI("http://reply.to"));
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://reply.to"));

    }

    @Test
    public void testReplyDoesntFollowFault() throws Exception {
        template.requestBody("direct:omittedReplyTo", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(anonymousUri);
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(new URI("http://fault.to"));

    }

    @Test
    public void testEmptyReplyAndFaultAndActionMustBePresent() throws Exception {
        template.requestBody("direct:empty", xmlBody);

        assertNotNull(sender.getMessageContext());

        // check default actions
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNotNull();
        Assertions.assertThat(wsaProperties.getAction()).isEqualTo(new URI("http://turnOnWSA.com"));
        Assertions.assertThat(wsaProperties.getReplyTo().getAddress()).isEqualTo(anonymousUri);
        Assertions.assertThat(wsaProperties.getFaultTo().getAddress()).isEqualTo(anonymousUri);

    }

    @Test
    public void testNoAction() throws Exception {
        template.requestBody("direct:noAction", xmlBody);

        assertNotNull(sender.getMessageContext());

        // WSA is not supported, if there is no ws action
        Assertions.assertThat(sender.getMessageContext()).isNotNull();
        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties((SoapMessage)sender.getMessageContext().getRequest());
        Assertions.assertThat(wsaProperties).isNull();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"org/apache/camel/component/spring/ws/addresing/ProducerParamsBasicTest-context.xml"});
    }
}
