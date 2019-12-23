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
package org.apache.camel.component.sip;

import java.util.Properties;

import javax.sip.ListeningPoint;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.message.Request;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.sip.listener.SipPublishListener;
import org.apache.camel.support.DefaultProducer;

public class SipPublisher extends DefaultProducer {
    private SipConfiguration configuration;
    private long sequenceNumber = 1;
    private SipPublishListener sipPublishListener;
    private SipProvider provider; 
    private SipStack sipStack;

    public SipPublisher(SipEndpoint sipEndpoint, SipConfiguration configuration) {
        super(sipEndpoint);
        this.setConfiguration(configuration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Properties properties = configuration.createInitialProperties();
        setSipStack(configuration.getSipFactory().createSipStack(properties));
        
        configuration.parseURI();
        if (sipPublishListener == null) {
            sipPublishListener = new SipPublishListener(this);
        }
        
        configuration.setListeningPoint(
                sipStack.createListeningPoint(configuration.getFromHost(), Integer.valueOf(configuration.getFromPort()).intValue(), configuration.getTransport()));
        
        boolean found = false;
        if (provider != null) {
            for (ListeningPoint listeningPoint : provider.getListeningPoints()) {
                if (listeningPoint.getIPAddress().equalsIgnoreCase(configuration.getListeningPoint().getIPAddress()) 
                    && (listeningPoint.getPort() == configuration.getListeningPoint().getPort())) {
                    found = true;
                }
            }
        }
        if (!found) {
            provider = getSipStack().createSipProvider(configuration.getListeningPoint());
            provider.addSipListener(sipPublishListener);
            configuration.setCallIdHeader(provider.getNewCallId());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        getSipStack().deleteListeningPoint(configuration.getListeningPoint());
        provider.removeSipListener(sipPublishListener);
        getSipStack().deleteSipProvider(provider);
        getSipStack().stop();
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        String requestMethod = exchange.getIn().getHeader("REQUEST_METHOD", String.class);
        if (requestMethod == null) {
            throw new CamelExchangeException("Missing mandatory Header: REQUEST_HEADER", exchange);
        }
        Object body = exchange.getIn().getBody();
        
        Request request = configuration.createSipRequest(sequenceNumber, requestMethod, body);
        provider.sendRequest(request);
    }

    public void setConfiguration(SipConfiguration configuration) {
        this.configuration = configuration;
    }

    public SipConfiguration getConfiguration() {
        return configuration;
    }

    public void setSipStack(SipStack sipStack) {
        this.sipStack = sipStack;
    }

    public SipStack getSipStack() {
        return sipStack;
    }

}
