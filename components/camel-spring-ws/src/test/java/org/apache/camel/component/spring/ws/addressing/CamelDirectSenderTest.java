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
import java.net.URISyntaxException;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.spring.ws.utils.OutputChannelReceiver;
import org.apache.camel.component.spring.ws.utils.TestUtil;
import org.fest.assertions.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;

/**
 * Provides abstract test for fault and output params for spring-ws:to: and
 * spring-ws:action: endpoints
 */
public class CamelDirectSenderTest extends AbstractWSATests {

    private OutputChannelReceiver customChannel;

    @EndpointInject(uri = "mock:camelDirect")
    private MockEndpoint endpointCamelDirect;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // initialize beans for catching results
        customChannel = getMandatoryBean(OutputChannelReceiver.class, "customReceiver");
        customChannel.clear();
    }

    /**
     * Only response is allow using a brand custom channel
     * 
     * @return
     */

    private MessageAddressingProperties customChannelParams() {
        assertNotNull(customChannel);
        assertNotNull(customChannel.getMessageContext());
        SoapMessage request = (SoapMessage)customChannel.getMessageContext().getRequest();
        assertNotNull(request);

        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties(request);
        assertNotNull(wsaProperties);
        assertNotNull(wsaProperties.getTo());
        return wsaProperties;
    }

    @Override
    public ActionCallback channelIn(String actionUri) throws URISyntaxException {
        // new channel
        return toAndReplyTo(actionUri, "mailto:andrej@chocolatejar.eu");
    }

    @Override
    public MessageAddressingProperties channelOut() {
        return newChannelParams();
    }

    @Test
    public void endpointSender() throws Exception {
        ActionCallback requestCallback = channelIn("http://sender-default.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(channelOut().getTo()).isEqualTo(new URI("mailto:andrej@chocolatejar.eu"));
        Assertions.assertThat(endpointCamelDirect.getReceivedCounter()).isZero();
    }

    @Test
    public void customSender() throws Exception {
        ActionCallback requestCallback = channelIn("http://sender-custom.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);

        Assertions.assertThat(customChannelParams().getTo()).isEqualTo(new URI("mailto:andrej@chocolatejar.eu"));
        Assertions.assertThat(endpointCamelDirect.getReceivedCounter()).isZero();
    }

    @Test
    public void camelInvalid() throws Exception {
        ActionCallback requestCallback = toAndReplyTo("http://sender-camel.com", "mailto:not-mappped-address@chocolatejar.eu");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        Assertions.assertThat(endpointCamelDirect.getReceivedCounter()).isZero();
    }

    @Test
    public void camelReceivedReplyTo() throws Exception {
        ActionCallback requestCallback = channelIn("http://sender-camel.com");

        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        endpointCamelDirect.assertExchangeReceived(0);
        endpointCamelDirect.assertIsSatisfied();
    }
    
    @Test
    public void customMessageIdGenerator() throws Exception {
        ActionCallback requestCallback = channelIn("http://messageIdStrategy-custom.com");
        
        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        
        Assertions.assertThat(channelOut().getMessageId()).isEqualTo(new URI("staticTestId"));
    }
    
    @Test
    public void defaultMessageIdGenerator() throws Exception {
        ActionCallback requestCallback = channelIn("http://messageIdStrategy-default.com");
        
        webServiceTemplate.sendSourceAndReceiveToResult(source, requestCallback, result);
        
        Assertions.assertThat(channelOut().getMessageId()).isNotEqualTo(new URI("staticTestId"));
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"org/apache/camel/component/spring/ws/addresing/CamelDirectSenderTest-context.xml"});
    }

}
