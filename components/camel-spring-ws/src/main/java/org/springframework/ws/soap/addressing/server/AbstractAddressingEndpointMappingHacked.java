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

package org.springframework.ws.soap.addressing.server;

import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import javax.xml.transform.TransformerException;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.server.EndpointInvocationChain;
import org.springframework.ws.server.EndpointMapping;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;
import org.springframework.ws.soap.addressing.messageid.MessageIdStrategy;
import org.springframework.ws.soap.addressing.messageid.UuidMessageIdStrategy;
import org.springframework.ws.soap.addressing.version.Addressing10;
import org.springframework.ws.soap.addressing.version.Addressing200408;
import org.springframework.ws.soap.addressing.version.AddressingVersion;
import org.springframework.ws.soap.server.SoapEndpointInvocationChain;
import org.springframework.ws.soap.server.SoapEndpointMapping;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.xml.transform.TransformerObjectSupport;

/**
 * THIS CLASS WILL BE REMOVED, WHEN THE FOLLOWING ISSUE WILL BE SOLVED: 
 * https://jira.springsource.org/browse/SWS-817
 * 
 * Abstract base class for {@link EndpointMapping} implementations that handle WS-Addressing. Besides the normal {@link
 * SoapEndpointMapping} properties, this mapping has a {@link #setVersions(org.springframework.ws.soap.addressing.version.AddressingVersion[])
 * versions} property, which defines the WS-Addressing specifications supported. By default, these are {@link
 * org.springframework.ws.soap.addressing.version.Addressing200408} and {@link org.springframework.ws.soap.addressing.version.Addressing10}.
 * <p/>
 * The {@link #setMessageIdStrategy(MessageIdStrategy) messageIdStrategy} property defines the strategy to use for
 * creating reply <code>MessageIDs</code>. By default, this is the {@link UuidMessageIdStrategy}.
 * <p/>
 * The {@link #setMessageSenders(WebServiceMessageSender[]) messageSenders} are used to send out-of-band reply messages.
 * If a request messages defines a non-anonymous reply address, these senders will be used to send the message.
 * <p/>
 * This mapping (and all subclasses) uses an implicit WS-Addressing {@link EndpointInterceptor}, which is added in every
 * {@link EndpointInvocationChain} produced. As such, this mapping does not have the standard <code>interceptors</code>
 * property, but rather a {@link #setPreInterceptors(EndpointInterceptor[]) preInterceptors} and {@link
 * #setPostInterceptors(EndpointInterceptor[]) postInterceptors} property, which are added before and after the implicit
 * WS-Addressing interceptor, respectively.
 *
 * @author Arjen Poutsma, Andrej Zachar
 * @since 1.5.0
 */
public abstract class AbstractAddressingEndpointMappingHacked extends TransformerObjectSupport
        implements SoapEndpointMapping, InitializingBean, Ordered {

    private String[] actorsOrRoles;

    private boolean isUltimateReceiver = true;

    private MessageIdStrategy messageIdStrategy;

    private WebServiceMessageSender[] messageSenders = new WebServiceMessageSender[0];

    private AddressingVersion[] versions;

    private EndpointInterceptor[] preInterceptors = new EndpointInterceptor[0];

    private EndpointInterceptor[] postInterceptors = new EndpointInterceptor[0];

    private int order = Integer.MAX_VALUE;  // default: same as non-Ordered


    /** Protected constructor. Initializes the default settings. */
    protected AbstractAddressingEndpointMappingHacked() {
        initDefaultStrategies();
    }

    /**
     * Initializes the default implementation for this mapping's strategies: the {@link
     * org.springframework.ws.soap.addressing.version.Addressing200408} and {@link org.springframework.ws.soap.addressing.version.Addressing10}
     * versions of the specification, and the {@link UuidMessageIdStrategy}.
     */
    protected void initDefaultStrategies() {
        this.versions = new AddressingVersion[]{new Addressing200408(), new Addressing10()};
        messageIdStrategy = new UuidMessageIdStrategy();
    }

    public final void setActorOrRole(String actorOrRole) {
        Assert.notNull(actorOrRole, "actorOrRole must not be null");
        actorsOrRoles = new String[]{actorOrRole};
    }

    public final void setActorsOrRoles(String[] actorsOrRoles) {
        Assert.notEmpty(actorsOrRoles, "actorsOrRoles must not be empty");
        this.actorsOrRoles = actorsOrRoles;
    }

    public final void setUltimateReceiver(boolean ultimateReceiver) {
        this.isUltimateReceiver = ultimateReceiver;
    }

    public final int getOrder() {
        return order;
    }

    /**
     * Specify the order value for this mapping.
     * <p/>
     * Default value is {@link Integer#MAX_VALUE}, meaning that it's non-ordered.
     *
     * @see org.springframework.core.Ordered#getOrder()
     */
    public final void setOrder(int order) {
        this.order = order;
    }
    

    /**
     * Set additional interceptors to be applied before the implicit WS-Addressing interceptor, e.g.
     * <code>XwsSecurityInterceptor</code>.
     */
    public final void setPreInterceptors(EndpointInterceptor[] preInterceptors) {
        Assert.notNull(preInterceptors, "'preInterceptors' must not be null");
        this.preInterceptors = preInterceptors;
    }

    /**
     * Set additional interceptors to be applied after the implicit WS-Addressing interceptor, e.g.
     * <code>PayloadLoggingInterceptor</code>.
     */
    public final void setPostInterceptors(EndpointInterceptor[] postInterceptors) {
        Assert.notNull(postInterceptors, "'postInterceptors' must not be null");
        this.postInterceptors = postInterceptors;
    }

    /**
     * Sets the message id strategy used for creating WS-Addressing MessageIds.
     * <p/>
     * By default, the {@link UuidMessageIdStrategy} is used.
     */
    public final void setMessageIdStrategy(MessageIdStrategy messageIdStrategy) {
        Assert.notNull(messageIdStrategy, "'messageIdStrategy' must not be null");
        this.messageIdStrategy = messageIdStrategy;
    }

    /**
     * Sets the single message sender used for sending messages.
     * <p/>
     * This message sender will be used to resolve an URI to a
     * {@link WebServiceConnection}.
     * 
     * @see #createConnection(URI)
     */
    public void setMessageSender(WebServiceMessageSender messageSender) {
        Assert.notNull(messageSender, "'messageSender' must not be null");
        setMessageSenders(new WebServiceMessageSender[] {messageSender});
    }

    public final void setMessageSenders(WebServiceMessageSender[] messageSenders) {
        Assert.notNull(messageSenders, "'messageSenders' must not be null");
        this.messageSenders = messageSenders;
    }

    public final WebServiceMessageSender[] getMessageSenders() {
        return this.messageSenders;
    }
    /**
     * Sets the WS-Addressing versions to be supported by this mapping.
     * <p/>
     * By default, this array is set to support {@link org.springframework.ws.soap.addressing.version.Addressing200408
     * the August 2004} and the {@link org.springframework.ws.soap.addressing.version.Addressing10 May 2006} versions of
     * the specification.
     */
    public final void setVersions(AddressingVersion[] versions) {
        this.versions = versions;
    }

    public void afterPropertiesSet() throws Exception {
        if (logger.isInfoEnabled()) {
            logger.info("Supporting " + Arrays.asList(versions));
        }
    }

    public final EndpointInvocationChain getEndpoint(MessageContext messageContext) throws TransformerException {
        Assert.isInstanceOf(SoapMessage.class, messageContext.getRequest());
        SoapMessage request = (SoapMessage) messageContext.getRequest();
        for (AddressingVersion version : versions) {
            if (supports(version, request)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Request [" + request + "] uses [" + version + "]");
                }
                MessageAddressingProperties requestMap = version.getMessageAddressingProperties(request);
                if (requestMap == null) {
                    return null;
                }
                Object endpoint = getEndpointInternal(requestMap);
                if (endpoint == null) {
                    return null;
                }
                return getEndpointInvocationChain(endpoint, version, requestMap);
            }
        }
        return null;
    }

    /**
     * Creates a {@link SoapEndpointInvocationChain} based on the given endpoint and {@link
     * org.springframework.ws.soap.addressing.version.AddressingVersion}.
     */
    private EndpointInvocationChain getEndpointInvocationChain(Object endpoint,
                                                               AddressingVersion version,
                                                               MessageAddressingProperties requestMap) {
        URI responseAction = getResponseAction(endpoint, requestMap);
        URI faultAction = getFaultAction(endpoint, requestMap);
        
        WebServiceMessageSender[] messageSenders = getMessageSenders(endpoint);
        MessageIdStrategy messageIdStrategy = getMessageStrategy(endpoint);
 
        EndpointInterceptor[] interceptors =
                new EndpointInterceptor[preInterceptors.length + postInterceptors.length + 1];
        System.arraycopy(preInterceptors, 0, interceptors, 0, preInterceptors.length);
        AddressingEndpointInterceptor interceptor = new AddressingEndpointInterceptor(version, messageIdStrategy,
                messageSenders, responseAction, faultAction);
        interceptors[preInterceptors.length] = interceptor;
        System.arraycopy(postInterceptors, 0, interceptors, preInterceptors.length + 1, postInterceptors.length);
        return new SoapEndpointInvocationChain(endpoint, interceptors, actorsOrRoles, isUltimateReceiver);
    }

    private boolean supports(AddressingVersion version, SoapMessage request) {
        SoapHeader header = request.getSoapHeader();
        if (header != null) {
            for (Iterator<SoapHeaderElement> iterator = header.examineAllHeaderElements(); iterator.hasNext();) {
                SoapHeaderElement headerElement = iterator.next();
                if (version.understands(headerElement)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    
    /**
     * Default implementation
     * 
     * @param endpoint specific message strategy
     * @return
     */
    protected MessageIdStrategy getMessageStrategy(Object endpoint) {
        return this.messageIdStrategy;
    }

    /**
     * Default implementation
     * 
     * @param endpoint
     * @return endpoint specific message senders
     */
    protected WebServiceMessageSender[] getMessageSenders(Object endpoint) {
        return this.messageSenders;
    }

    /**
     * Lookup an endpoint for the given  {@link MessageAddressingProperties}, returning <code>null</code> if no specific
     * one is found. This template method is called by {@link #getEndpoint(MessageContext)}.
     *
     * @param map the message addressing properties
     * @return the endpoint, or <code>null</code>
     */
    protected abstract Object getEndpointInternal(MessageAddressingProperties map);

    /**
     * Provides the WS-Addressing Action for response messages, given the endpoint, and request Message Addressing
     * Properties.
     *
     * @param endpoint   the mapped endpoint
     * @param requestMap the MAP for the request
     * @return the response Action
     */
    protected abstract URI getResponseAction(Object endpoint, MessageAddressingProperties requestMap);

    /**
     * Provides the WS-Addressing Action for response fault messages, given the endpoint, and request Message Addressing
     * Properties.
     *
     * @param endpoint   the mapped endpoint
     * @param requestMap the MAP for the request
     * @return the response Action
     */
    protected abstract URI getFaultAction(Object endpoint, MessageAddressingProperties requestMap);

}
