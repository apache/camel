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
import javax.sip.header.FromHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.sip.SipConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SipListener which will listen for and process NOTIFY and MESSAGE requests events
 */
public class SipSubscriptionListener implements SipListener {

    /**
     * Logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(SipSubscriptionListener.class);

    /**
     * The camel consumer to give the incoming SIP messages
     */
    private SipConsumer sipConsumer;

    /**
     * The potential dialog between this retrieving SIP point and the sender
     */
    private Dialog subscriberDialog;

    /**
     * The dialog for when the sender made one
     */
    private Dialog forkedDialog;

    /**
     * Create a SipListener listening for subscription events
     * @param sipConsumer the camel consumer creating this listener
     */
    public SipSubscriptionListener(SipConsumer sipConsumer) {
        this.setSipConsumer(sipConsumer);
    }

    /**
     * Sends the message body from a SIP request event along the camel route.
     * @param response the message body from the SIP request
     * @throws CamelException when dispatching the message goes wrong
     */
    private void dispatchExchange(Object response) throws CamelException {
        LOG.debug("Consumer Dispatching the received notification along the route");
        Exchange exchange = sipConsumer.getEndpoint().createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody(response);

        //try to send the message along
        try
        {
            sipConsumer.getProcessor().process(exchange);
        }
        catch (Exception e)
        {
            throw new CamelException("Error in consumer while dispatching exchange", e);
        }
    }

    /**
     * process incoming SIP requests. Only processes NOTIFY and MESSAGE requests
     * @param requestReceivedEvent the request event
     */
    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransactionId = requestReceivedEvent
                .getServerTransaction();
        String viaBranch = ((ViaHeader)(request.getHeaders(ViaHeader.NAME).next())).getParameter("branch");
        String fromBranch = ((FromHeader)(request.getHeader(FromHeader.NAME))).toString();

        //log the retrieved request
        LOG.debug("Request: {}", request.getMethod());
        LOG.debug("Server Transaction Id: {}", serverTransactionId);
        LOG.debug("Received From Branch: {}", viaBranch);
        LOG.debug("Received From: {}", fromBranch);

        //process NOTIFY and MESSAGE requests only
        if (Request.NOTIFY.equals(request.getMethod()))
        {
            processNotify(requestReceivedEvent, serverTransactionId);
        }
        else if(Request.MESSAGE.equals(request.getMethod()))
        {
            processMessage(requestReceivedEvent, serverTransactionId);
        }
        else
        {
            LOG.debug("Failed to process the SIP request because the method was " + request.getMethod());
        }
    }

    /**
     * processes notification SIP messages by dispatching it along the camel route and sending a
     * success response to the notifier
     * @param requestEvent the notify request message
     * @param serverTransactionId the transactionID from the request
     */
    public synchronized void processNotify(RequestEvent requestEvent,
            ServerTransaction serverTransactionId)
    {
        LOG.debug("Notification received at consumer");
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
            // Dispatch the response along the route
            dispatchExchange(notify.getContent());
            
            // Send back a success response
            Response response = sipConsumer.getConfiguration().getMessageFactory().createResponse(200, notify);
            response.addHeader(sipConsumer.getConfiguration().getContactHeader());
            serverTransactionId.sendResponse(response);

            // Check if subscription is terminated
            SubscriptionStateHeader subscriptionState = (SubscriptionStateHeader) notify
                    .getHeader(SubscriptionStateHeader.NAME);
            if (subscriptionState.getState().equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
                LOG.info("Subscription state is terminated. Deleting the current dialog");
                dialog.delete();
            }
        }
        catch (Exception e)
        {
            LOG.error("Exception thrown during Notify processing in the SipSubscriptionListener.", e);
        }
    }

    public synchronized void processMessage(RequestEvent requestEvent,
                                            ServerTransaction serverTransactionId)
    {
        LOG.debug("Message received at consumer");
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

            // Dispatch the response along the route
            dispatchExchange(notify.getContent());

            // Send back a success response
            Response response = sipConsumer.getConfiguration().getMessageFactory().createResponse(200, notify);
            response.addHeader(sipConsumer.getConfiguration().getContactHeader());
            serverTransactionId.sendResponse(response);
        }
        catch (Exception e)
        {
            LOG.error("Exception thrown during Notify processing in the SipSubscriptionListener.", e);
        }
    }
    
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

    public void processIOException(IOExceptionEvent exceptionEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("IOExceptionEvent received at Sip Subscription Listener");
        }
    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("TransactionTerminatedEvent received at Sip Subscription Listener");
        }
    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("DialogTerminatedEvent received at Sip Subscription Listener");
        }
    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("TimeoutEvent received at Sip Subscription Listener");
        }
    }

    public void setSipConsumer(SipConsumer sipConsumer) {
        this.sipConsumer = sipConsumer;
    }

    public SipConsumer getSipConsumer() {
        return sipConsumer;
    }

    public Dialog getForkedDialog() {
        return forkedDialog;
    }
}
