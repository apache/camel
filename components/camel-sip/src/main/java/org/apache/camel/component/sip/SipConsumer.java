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
package org.apache.camel.component.sip;

import java.util.Properties;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.ListeningPoint;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.message.Request;

import org.apache.camel.Processor;
import org.apache.camel.component.sip.listener.SipSubscriptionListener;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camel consumer which subscribes to events from the given URI or handles incoming MESSAGE requests
 */
public class SipConsumer extends DefaultConsumer {

    /**
     * The logger of this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(SipConsumer.class);

    /**
     * The configuration set by the SIP URI. Constructor sets the "consumer" value of the configuration to "true".
     */
    private SipConfiguration configuration;

    /**
     * entity which listens for and processes any incoming sip request and responses
     */
    private SipSubscriptionListener sipSubscriptionListener;

    /**
     * SIP entity which is responsible for sending requests and responses
     */
    private SipProvider provider;

    /**
     * The Dialog that will store the SIP relationship between the subscribing entity and the notifying entity
     */
    private Dialog subscriberDialog;

    /**
     * SipStack used to create Listening points on the given sip URI and to create a SipProvider to send messages back
     */
    private SipStack sipStack;

    /**
     * Creates a consumer which subscribes to the given SIP uri in the configuration
     *
     * @param sipEndpoint the endpoint creating the consumer
     * @param processor the processor in the camel route
     * @param configuration the SIP configuration holding the URI and additional information
     */
    public SipConsumer(SipEndpoint sipEndpoint, Processor processor, SipConfiguration configuration) {
        super(sipEndpoint, processor);
        this.configuration = configuration;
        this.configuration.setConsumer(true);
    }

    /**
     * Starts the consumer by creating listening points and potentially sending a SIP subscribe request
     *
     * @throws Exception when sending the subscription goes wrong
     */
    @Override
    protected void doStart() throws Exception {
        super.doStart();

        //set up the SipConfiguration for the consumer and create the SIP entities necessary for
        //sending and receiving SIP messages
        Properties properties = configuration.createInitialProperties();
        LOG.trace("The keyset in the properties: {}", properties.keySet());

        sipStack = configuration.getSipFactory().createSipStack(properties);
        configuration.parseURI();
        sipSubscriptionListener = new SipSubscriptionListener(this);

        //listening point for SIP request and responses
        LOG.debug("Making listening point on address: {}:{} with transport {}",
                configuration.getFromHost(),
                configuration.getFromPort(),
                configuration.getTransport());
        ListeningPoint listeningPoint = sipStack.createListeningPoint(
                configuration.getFromHost(),
                configuration.getFromPort(),
                configuration.getTransport());

        //set the listening points
        configuration.setListeningPoint(listeningPoint);
        provider = sipStack.createSipProvider(configuration.getListeningPoint());
        provider.addSipListener(sipSubscriptionListener);

        if (configuration.getCallIdHeader() == null) {
            configuration.setCallIdHeader(provider.getNewCallId());
        }
        
        if(configuration.isSubscribing())
        {
            // Create the Subscription request to register with the presence agent and receive notifications.
            configuration.setCallIdHeader(provider.getNewCallId());
            Request request = configuration.createSipRequest(1, Request.SUBSCRIBE, configuration.getEventHeaderName());

            // Create the subscriber transaction from request.
            ClientTransaction subscriberTransactionId = provider.getNewClientTransaction(request);

            // Add an Event header for the subscription.
            request.addHeader(configuration.getEventHeader());
            subscriberDialog = subscriberTransactionId.getDialog();

            // Send the outgoing subscription request.
            subscriberTransactionId.sendRequest();
            LOG.debug("Sent subscription request to " + subscriberTransactionId.getDialog().getRemoteParty());
        }
    }

    /**
     * Stops the consumer
     *
     * @throws Exception
     */
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        provider.removeSipListener(sipSubscriptionListener);
        sipStack.deleteListeningPoint(configuration.getListeningPoint());
        sipStack.deleteSipProvider(provider);
        sipStack.stop();
    }

    /**
     * for getting and setting the SipConfiguration
     */
    public SipConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SipConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * for getting and setting the SipSubscriptionListener
     */
    public void setSipSubscriptionListener(SipSubscriptionListener sipSubscriptionListener) {
        this.sipSubscriptionListener = sipSubscriptionListener;
    }

    public SipSubscriptionListener getSipSubscriptionListener() {
        return sipSubscriptionListener;
    }

    /**
     * for getting and setting the SipStack
     */
    public void setSipStack(SipStack sipStack) {
        this.sipStack = sipStack;
    }

    public SipStack getSipStack() {
        return sipStack;
    }

    /**
     * for getting and setting the SipProvider
     */
    public SipProvider getProvider() {
        return provider;
    }

    public void setProvider(SipProvider provider) {
        this.provider = provider;
    }

    /**
     * for getting the dialog between the subscriber and the notifier
     */
    public Dialog getSubscriberDialog() {
        return subscriberDialog;
    }

}
