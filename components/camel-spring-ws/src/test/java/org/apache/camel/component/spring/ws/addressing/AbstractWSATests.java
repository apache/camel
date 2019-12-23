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

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.component.spring.ws.utils.OutputChannelReceiver;
import org.apache.camel.component.spring.ws.utils.TestUtil;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.After;
import org.junit.Before;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.addressing.core.EndpointReference;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;
import org.springframework.ws.soap.addressing.version.Addressing10;

/**
 * Provides abstract test for WS-Addressing
 */
public abstract class AbstractWSATests extends CamelSpringTestSupport {

    protected WebServiceTemplate webServiceTemplate;
    protected OutputChannelReceiver response;
    protected OutputChannelReceiver newReply;
    protected StreamSource source;
    protected StreamResult result;

    private final String xmlBody = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";
    private String requestInputAction;
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // initialize beans for catching results
        webServiceTemplate = applicationContext.getBean("webServiceTemplate", WebServiceTemplate.class);
        newReply = getMandatoryBean(OutputChannelReceiver.class, "replyReceiver");
        response = getMandatoryBean(OutputChannelReceiver.class, "responseReceiver");

        // sample data
        source = new StreamSource(new StringReader(xmlBody));
        result = new StreamResult(new StringWriter());

        // reset from previous test
        response.clear();
        newReply.clear();
        requestInputAction = null;
    }

    @After
    public void after() throws Exception {
        assertNotNull(result);
    }

    /**
     * Creates WS-Addressing Action and ReplyTo param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback actionAndReplyTo(String action, String replyTo) throws URISyntaxException {
        requestInputAction = action;
        ActionCallback requestCallback = new ActionCallback(action);
        if (replyTo != null) {
            requestCallback.setReplyTo(new EndpointReference(new URI(replyTo)));
        }
        return requestCallback;
    }

    /**
     * Creates WS-Addressing Action param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback action(String action) throws URISyntaxException {
        return actionAndReplyTo(action, null);
    }

    /**
     * Creates WS-Addressing To and ReplyTo param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback toAndReplyTo(String to, String replyTo) throws URISyntaxException {
        requestInputAction = "http://doesn-not-matter.com";
        ActionCallback requestCallback = new ActionCallback(new URI(requestInputAction), new Addressing10(), new URI(to));
        if (replyTo != null) {
            requestCallback.setReplyTo(new EndpointReference(new URI(replyTo)));
        }
        return requestCallback;
    }

    /**
     * Creates WS-Addressing To param for request
     * 
     * @param action
     * @param replyTo
     * @return
     * @throws URISyntaxException
     */
    protected final ActionCallback to(String to) throws URISyntaxException {
        return toAndReplyTo(to, null);
    }

    /**
     * Construct a default action for the response message from the input
     * message using the default response action suffix.
     * 
     * @return
     * @throws URISyntaxException
     */
    protected URI getDefaultResponseAction() throws URISyntaxException {
        return new URI(requestInputAction + "Response");
    }

    /**
     * Only response is allow using a brand new channel
     * 
     * @return
     */

    protected final MessageAddressingProperties newChannelParams() {
        assertNotNull(newReply);
        assertNotNull(newReply.getMessageContext());
        SoapMessage request = (SoapMessage)newReply.getMessageContext().getRequest();
        assertNotNull(request);

        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties(request);
        assertNotNull(wsaProperties);
        assertNotNull(wsaProperties.getTo());
        return wsaProperties;
    }

    /**
     * Only response is allow using same channel
     * 
     * @return
     */
    protected final MessageAddressingProperties sameChannelParams() {
        // we expect the same channel reply
        assertNull(newReply.getMessageContext());

        assertNotNull(response);
        assertNotNull(response.getMessageContext());

        SoapMessage soapResponse = (SoapMessage)response.getMessageContext().getResponse();
        assertNotNull(soapResponse);

        MessageAddressingProperties wsaProperties = TestUtil.getWSAProperties(soapResponse);
        assertNotNull(wsaProperties);
        return wsaProperties;
    }

    /**
     * Provides such an ActionCallback that sets the WS-Addressing param replyTo
     * or doesn't set WS-Addressing param replyTo. In other words it cause
     * response to be return using new or same channel as the request.
     * 
     * @param action
     * @return
     * @throws URISyntaxException
     */
    abstract ActionCallback channelIn(String action) throws URISyntaxException;

    /**
     * Provide corresponding results based on channel input. These two abstract
     * methods (channelIn and channelOut)are bind together tighly.
     * 
     * @return
     */
    abstract MessageAddressingProperties channelOut();

}
