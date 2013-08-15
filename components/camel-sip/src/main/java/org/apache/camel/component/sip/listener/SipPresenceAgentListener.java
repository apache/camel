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
package org.apache.camel.component.sip.listener;

import java.text.ParseException;
import java.util.UUID;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.SipURI;
import javax.sip.header.EventHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.camel.component.sip.SipPresenceAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipPresenceAgentListener implements SipListener, SipMessageCodes {
    private static final Logger LOG = LoggerFactory.getLogger(SipPresenceAgentListener.class);
    protected Dialog dialog;
    protected int notifyCount;
    private SipPresenceAgent sipPresenceAgent;

    public SipPresenceAgentListener(SipPresenceAgent sipPresenceAgent) {
        this.sipPresenceAgent = sipPresenceAgent;
    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId = requestEvent.getServerTransaction();

        LOG.debug("Request: {}", request.getMethod()); 
        LOG.debug("Server Transaction Id: {}", serverTransactionId);

        if (request.getMethod().equals(Request.SUBSCRIBE)) {
            processSubscribe(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.PUBLISH)) {
            processPublish(requestEvent, serverTransactionId);
        } else {
            LOG.debug("Received expected request with method: {}. No further processing done", request.getMethod());
        }
    }
    
    private void sendNotification(EventHeader eventHeader, boolean isInitial, Object body) throws SipException, ParseException {
        /*
         * NOTIFY requests MUST contain a "Subscription-State" header with a
         * value of "active", "pending", or "terminated". The "active" value
         * indicates that the subscription has been accepted and has been
         * authorized (in most cases; see section 5.2.). The "pending" value
         * indicates that the subscription has been received, but that
         * policy information is insufficient to accept or deny the
         * subscription at this time. The "terminated" value indicates that
         * the subscription is not active.
         */
        
        Request notifyRequest = dialog.createRequest("NOTIFY");

        // Mark the contact header, to check that the remote contact is updated
        ((SipURI)sipPresenceAgent.getConfiguration().getContactHeader().getAddress().getURI()).setParameter(
                sipPresenceAgent.getConfiguration().getFromUser(), sipPresenceAgent.getConfiguration().getFromHost());

        SubscriptionStateHeader sstate;
        if (isInitial) {
            // Initial state is pending, second time we assume terminated (Expires==0)
            sstate = 
                sipPresenceAgent.getConfiguration().getHeaderFactory().createSubscriptionStateHeader(isInitial ? SubscriptionStateHeader.PENDING : SubscriptionStateHeader.TERMINATED);
    
            // Need a reason for terminated
            if (sstate.getState().equalsIgnoreCase("terminated")) {
                sstate.setReasonCode("deactivated");
            }
        } else {
            sstate = sipPresenceAgent.getConfiguration().getHeaderFactory().createSubscriptionStateHeader(SubscriptionStateHeader.ACTIVE);
        }

        notifyRequest.addHeader(sstate);
        notifyRequest.setHeader(eventHeader);
        notifyRequest.setHeader(sipPresenceAgent.getConfiguration().getContactHeader());
        notifyRequest.setContent(body, sipPresenceAgent.getConfiguration().getContentTypeHeader());
        LOG.debug("Sending the following NOTIFY request to Subscriber: {}", notifyRequest);
        
        ClientTransaction clientTransactionId = sipPresenceAgent.getProvider().getNewClientTransaction(notifyRequest);

        dialog.sendRequest(clientTransactionId);
    }
    
    private void processPublish(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        try {
            Request request = requestEvent.getRequest();
            LOG.debug("SipPresenceAgentListener: Received a Publish request, sending OK");
            LOG.debug("SipPresenceAgentListener request: {}", request);
            EventHeader eventHeader = (EventHeader) requestEvent.getRequest().getHeader(EventHeader.NAME);
            Response response = sipPresenceAgent.getConfiguration().getMessageFactory().createResponse(202, request);
            sipPresenceAgent.getProvider().sendResponse(response);

            // Send notification to subscriber
            sendNotification(eventHeader, false, request.getContent());
                     
        } catch (Exception e) {
            LOG.error("Exception thrown during publish/notify processing in the Sip Presence Agent Listener", e);
        }
    }

    public void processSubscribe(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        try {
            LOG.debug("SipPresenceAgentListener: Received a Subscribe request, sending OK");
            LOG.debug("SipPresenceAgentListener request: {}", request);
            EventHeader eventHeader = (EventHeader) request.getHeader(EventHeader.NAME);
            if (eventHeader == null) {
                LOG.debug("Cannot find event header.... dropping request.");
                return;
            }

            // Always create a ServerTransaction, best as early as possible in the code
            Response response = null;
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }

            // Check if it is an initial SUBSCRIBE or a refresh / unsubscribe
            boolean isInitial = requestEvent.getDialog() == null;
            if (isInitial) {
                String toTag = UUID.randomUUID().toString();
                response = sipPresenceAgent.getConfiguration().getMessageFactory().createResponse(202, request);
                ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
                toHeader.setTag(toTag); // Application is supposed to set.

                this.dialog = st.getDialog();
                // subscribe dialogs do not terminate on bye.
                this.dialog.terminateOnBye(false);
            } else {
                response = sipPresenceAgent.getConfiguration().getMessageFactory().createResponse(200, request);
            }

            // Both 2xx response to SUBSCRIBE and NOTIFY need a Contact
            response.addHeader(sipPresenceAgent.getConfiguration().getContactHeader());

            // Expires header is mandatory in 2xx responses to SUBSCRIBE
            response.addHeader(sipPresenceAgent.getConfiguration().getExpiresHeader());
            st.sendResponse(response);
            
            LOG.debug("SipPresenceAgentListener: Sent OK Message");
            LOG.debug("SipPresenceAgentListener response: {}", response);
            sendNotification(eventHeader, isInitial, request.getContent());

        } catch (Throwable e) {
            LOG.error("Exception thrown during Notify processing in the SipPresenceAgentListener.", e);
        }
    }

    public synchronized void processResponse(ResponseEvent responseReceivedEvent) {
        Response response = responseReceivedEvent.getResponse();
        Integer statusCode = response.getStatusCode();
        if (SIP_MESSAGE_CODES.containsKey(statusCode)) {
            LOG.debug(SIP_MESSAGE_CODES.get(statusCode) + " received from Subscriber");
        }
    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("TimeoutEvent received at Sip Subscription Listener");
        }
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("IOExceptionEvent received at SipPresenceAgentListener");
        }
    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("TransactionTerminatedEvent received at SipPresenceAgentListener");
        }
    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("DialogTerminatedEvent received at SipPresenceAgentListener");
        }
    }

}
