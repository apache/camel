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

public class SipSubscriber extends DefaultConsumer {
    private SipConfiguration configuration;
    private SipSubscriptionListener sipSubscriptionListener;
    private SipProvider provider;
    private Dialog subscriberDialog;
    private SipStack sipStack;

    public SipSubscriber(SipEndpoint sipEndpoint, Processor processor, SipConfiguration configuration) {
        super(sipEndpoint, processor);
        this.configuration = configuration;
        this.configuration.setConsumer(true);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Properties properties = configuration.createInitialProperties();
        sipStack = configuration.getSipFactory().createSipStack(properties);
        
        configuration.parseURI();
        sipSubscriptionListener = new SipSubscriptionListener(this);
        ListeningPoint listeningPoint = 
            sipStack.createListeningPoint(configuration.getFromHost(), Integer.valueOf(configuration.getFromPort()).intValue(), configuration.getTransport());
        configuration.setListeningPoint(listeningPoint);
        provider = sipStack.createSipProvider(configuration.getListeningPoint());
        provider.addSipListener(sipSubscriptionListener);
        
        if (configuration.getCallIdHeader() == null) {
            configuration.setCallIdHeader(provider.getNewCallId());
        }
        
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
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop(); 
    }

    public SipConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SipConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setSipSubscriptionListener(SipSubscriptionListener sipSubscriptionListener) {
        this.sipSubscriptionListener = sipSubscriptionListener;
    }

    public SipSubscriptionListener getSipSubscriptionListener() {
        return sipSubscriptionListener;
    }

    public void setSipStack(SipStack sipStack) {
        this.sipStack = sipStack;
    }

    public SipStack getSipStack() {
        return sipStack;
    }

    public SipProvider getProvider() {
        return provider;
    }

    public void setProvider(SipProvider provider) {
        this.provider = provider;
    }

    public Dialog getSubscriberDialog() {
        return subscriberDialog;
    }

}
