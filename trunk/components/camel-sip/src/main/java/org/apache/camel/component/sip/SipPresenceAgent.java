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

import javax.sip.SipProvider;
import javax.sip.SipStack;

import org.apache.camel.Processor;
import org.apache.camel.component.sip.listener.SipPresenceAgentListener;
import org.apache.camel.impl.DefaultConsumer;

public class SipPresenceAgent extends DefaultConsumer {
    private SipConfiguration configuration;
    private SipPresenceAgentListener sipPresenceAgentListener;
    private SipProvider provider; 
    private SipStack sipStack;
    
    public SipPresenceAgent(SipEndpoint sipEndpoint, Processor processor,
        SipConfiguration configuration) {
        super(sipEndpoint, processor);
        this.configuration = configuration;
        this.configuration.setConsumer(true);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Properties properties = configuration.createInitialProperties();
        setSipStack(configuration.getSipFactory().createSipStack(properties));
        
        configuration.parseURI();
        sipPresenceAgentListener = new SipPresenceAgentListener(this);
        configuration.setListeningPoint(
                sipStack.createListeningPoint(configuration.getFromHost(), 
                    Integer.valueOf(configuration.getFromPort()).intValue(), 
                    configuration.getTransport()));
        provider = getSipStack().createSipProvider(configuration.getListeningPoint());
        provider.addSipListener(sipPresenceAgentListener);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop(); 
        getSipStack().deleteListeningPoint(configuration.getListeningPoint());
        provider.removeSipListener(sipPresenceAgentListener);
        getSipStack().deleteSipProvider(provider);
        getSipStack().stop();
    }

    public SipConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SipConfiguration configuration) {
        this.configuration = configuration;
    }

    public SipProvider getProvider() {
        return provider;
    }

    public void setProvider(SipProvider provider) {
        this.provider = provider;
    }

    public void setSipStack(SipStack sipStack) {
        this.sipStack = sipStack;
    }

    public SipStack getSipStack() {
        return sipStack;
    }
    
}
