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

import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.sip.SipSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipSubscriptionListener implements SipListener {
    private static final Logger LOG = LoggerFactory.getLogger(SipSubscriptionListener.class);
    private SipSubscriber sipSubscriber;
    private Dialog subscriberDialog;
    private Dialog forkedDialog;

    public SipSubscriptionListener(SipSubscriber sipSubscriber) {
        this.setSipSubscriber(sipSubscriber);
    }

    private void dispatchExchange(Object response) throws CamelException {
        LOG.debug("Consumer Dispatching the received notification along the route");
        Exchange exchange = sipSubscriber.getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(response);
        try {
            sipSubscriber.getProcessor().process(exchange);
        } catch (Exception e) {
            throw new CamelException("Error in consumer while dispatching exchange", e);
        }
    }
    
    @Override
    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransactionId = requestReceivedEvent
                .getServerTransaction();
        String viaBranch = ((ViaHeader)(request.getHeaders(ViaHeader.NAME).next())).getParameter("branch");
        LOG.debug("Request: {}", request.getMethod()); 
        LOG.debug("Server Transaction Id: {}", serverTransactionId);
        LOG.debug("Received From Branch: {}", viaBranch);

        if (request.getMethod().equals(Request.NOTIFY)) {
            processNotify(requestReceivedEvent, serverTransactionId);
        } 
    }

    public synchronized void processNotify(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {
        LOG.debug("Notification received at Subscriber");
        SipProvider provider = (SipProvider) requestEvent.getSource();
        Request notify = requestEvent.getRequest();
        try {
            if (serverTransactionId == null) {
                LOG.info("ServerTransaction is null. Creating new Server transaction");
                serverTransactionId = provider.getNewServerTransaction(notify);
            }
            Dialog dialog = serverTransactionId.getDialog();

            if (dialog != subscriberDialog) {
                forkedDialog = dialog;
            }
            //Dispatch the response along the route
            dispatchExchange(notify.getContent());
            
            // Send back an success response
            Response response = sipSubscriber.getConfiguration().getMessageFactory().createResponse(200, notify);            
            response.addHeader(sipSubscriber.getConfiguration().getContactHeader());
            serverTransactionId.sendResponse(response);

            SubscriptionStateHeader subscriptionState = (SubscriptionStateHeader) notify
                    .getHeader(SubscriptionStateHeader.NAME);

            // Subscription is terminated?
            if (subscriptionState.getState().equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
                LOG.info("Subscription state is terminated. Deleting the current dialog");
                dialog.delete();
            }
        } catch (Exception e) {
            LOG.error("Exception thrown during Notify processing in the SipSubscriptionListener.", e);
        }
    }
    
    @Override
    public void processResponse(ResponseEvent responseReceivedEvent) {
        LOG.debug("Response received at Subscriber");
        Response response = responseReceivedEvent.getResponse();
        Transaction clientTransactionId = responseReceivedEvent.getClientTransaction();

        LOG.debug("Response received with client transaction id {}:{}", clientTransactionId, response.getStatusCode());
        if (clientTransactionId == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Stray response -- dropping");
            }
            return;
        }
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("IOExceptionEvent received at Sip Subscription Listener");
        }
    }

    @Override
    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("TransactionTerminatedEvent received at Sip Subscription Listener");
        }
    }

    @Override
    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("DialogTerminatedEvent received at Sip Subscription Listener");
        }
    }

    @Override
    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("TimeoutEvent received at Sip Subscription Listener");
        }
    }

    public void setSipSubscriber(SipSubscriber sipSubscriber) {
        this.sipSubscriber = sipSubscriber;
    }

    public SipSubscriber getSipSubscriber() {
        return sipSubscriber;
    }

    public Dialog getForkedDialog() {
        return forkedDialog;
    }
}
