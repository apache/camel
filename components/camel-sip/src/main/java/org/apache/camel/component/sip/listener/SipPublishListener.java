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
package org.apache.camel.component.sip.listener;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.TransactionTerminatedEvent;

import org.apache.camel.component.sip.SipPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipPublishListener implements SipListener {
    private static final Logger LOG = LoggerFactory.getLogger(SipPublishListener.class);
    private SipPublisher sipPublisher;

    public SipPublishListener(SipPublisher sipPublisher) {
        this.setSipPublisher(sipPublisher);
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        // The SipPublishListener associated with the SipPublisher
        // may not accept incoming requests 
    }

    @Override
    public void processResponse(ResponseEvent responseReceivedEvent) {
        // The SipPublishListener sends InOnly requests to the Presence Agent
        // and only receives ACKs from the Presence Agent to satisfy the 
        // Sip handshakeand. Hence any responses are not further processed.
    }
    
    @Override
    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("processTimeout received at Sip Publish Listener");
        }
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("processDialogTerminated received at Sip Publish Listener");
        }
    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("IOExceptionEvent received at Sip Publish Listener");
        }       
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("processTransactionTerminated received at Sip Publish Listener");
        }
    }

    public void setSipPublisher(SipPublisher sipPublisher) {
        this.sipPublisher = sipPublisher;
    }

    public SipPublisher getSipPublisher() {
        return sipPublisher;
    }
}
