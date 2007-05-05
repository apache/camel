/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf.transport;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.io.AbstractCachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.wsdl.EndpointReferenceUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @version $Revision$
 */
public class CamelDestination extends AbstractDestination implements Configurable {
    protected static final String BASE_BEAN_NAME_SUFFIX = ".camel-destination-base";
    private static final Logger LOG = LogUtils.getL7dLogger(CamelDestination.class);
    CamelContext camelContext;
    String camelUri;
    final ConduitInitiator conduitInitiator;
    private CamelTransportBase base;
    private Endpoint endpoint;

    public CamelDestination(CamelContext camelContext, Bus bus,
            ConduitInitiator ci,
            EndpointInfo info) throws IOException {
        super(getTargetReference(info, bus), info);
        this.camelContext = camelContext;

        base = new CamelTransportBase(camelContext, bus, endpointInfo, true, BASE_BEAN_NAME_SUFFIX);

        conduitInitiator = ci;

        initConfig();
    }

    protected Logger getLogger() {
        return LOG;
    }

    /**
     * @param inMessage the incoming message
     * @return the inbuilt backchannel
     */
    protected Conduit getInbuiltBackChannel(Message inMessage) {
        return new BackChannelConduit(EndpointReferenceUtils.getAnonymousEndpointReference(),
                inMessage);
    }

    public void activate() {
        getLogger().log(Level.INFO, "CamelDestination activate().... ");

        try {
            getLogger().log(Level.FINE, "establishing Camel connection");
            endpoint = camelContext.getEndpoint(camelUri);
        }
        catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Camel connect failed with EException : ", ex);
        }
    }

    public void deactivate() {
        base.close();
    }

    public void shutdown() {
        getLogger().log(Level.FINE, "CamelDestination shutdown()");
        this.deactivate();
    }

    protected void incoming(Exchange exchange) {
        getLogger().log(Level.FINE, "server received request: ", exchange);

        byte[] bytes = base.unmarshal(exchange);

        // get the message to be interceptor
        MessageImpl inMessage = new MessageImpl();
        inMessage.setContent(InputStream.class, new ByteArrayInputStream(bytes));
        base.populateIncomingContext(exchange, inMessage, CamelConstants.CAMEL_SERVER_REQUEST_HEADERS);
        //inMessage.put(CamelConstants.CAMEL_SERVER_RESPONSE_HEADERS, new CamelMessageHeadersType());
        inMessage.put(CamelConstants.CAMEL_REQUEST_MESSAGE, exchange);

        inMessage.setDestination(this);

        //handle the incoming message
        incomingObserver.onMessage(inMessage);
    }

    public String getBeanName() {
        return endpointInfo.getName().toString() + ".camel-destination";
    }

    private void initConfig() {
/*
        this.runtimePolicy = endpointInfo.getTraversedExtensor(new ServerBehaviorPolicyType(),
                                                               ServerBehaviorPolicyType.class);
        this.serverConfig = endpointInfo.getTraversedExtensor(new ServerConfig(), ServerConfig.class);
        this.address = endpointInfo.getTraversedExtensor(new AddressType(), AddressType.class);
        this.sessionPool = endpointInfo.getTraversedExtensor(new SessionPoolType(), SessionPoolType.class);
*/
    }

    protected class ConsumerProcessor implements Processor {
        public void process(Exchange exchange) {
            try {
                incoming(exchange);
            }
            catch (Throwable ex) {
                getLogger().log(Level.WARNING, "Failed to process incoming message : ", ex);
            }
        }
    }

    // this should deal with the cxf message
    protected class BackChannelConduit extends AbstractConduit {
        protected Message inMessage;

        BackChannelConduit(EndpointReferenceType ref, Message message) {
            super(ref);
            inMessage = message;
        }

        /**
         * Register a message observer for incoming messages.
         *
         * @param observer the observer to notify on receipt of incoming
         */
        public void setMessageObserver(MessageObserver observer) {
            // shouldn't be called for a back channel conduit
        }

        /**
         * Send an outbound message, assumed to contain all the name-value
         * mappings of the corresponding input message (if any).
         *
         * @param message the message to be sent.
         */
        public void prepare(Message message) throws IOException {
            // setup the message to be send back
            message.put(CamelConstants.CAMEL_REQUEST_MESSAGE,
                    inMessage.get(CamelConstants.CAMEL_REQUEST_MESSAGE));
            message.setContent(OutputStream.class,
                    new CamelOutputStream(inMessage));
        }

        protected Logger getLogger() {
            return LOG;
        }

    }

    private class CamelOutputStream extends AbstractCachedOutputStream {
        private Message inMessage;
        private Producer<Exchange> replyTo;
        private Producer<Exchange> sender;

        // setup the ByteArrayStream
        public CamelOutputStream(Message m) {
            super();
            inMessage = m;
        }

        // prepair the message and get the send out message
        private void commitOutputMessage() throws IOException {

            //setup the reply message
            final String replyToUri = getReplyToDestination(inMessage);

            base.template.send(replyToUri, new Processor() {
                public void process(Exchange reply) {
                    base.marshal(currentStream.toString(), replyToUri, reply);

                    setReplyCorrelationID(inMessage, reply);

                    base.setMessageProperties(inMessage, reply);

                    getLogger().log(Level.FINE, "just server sending reply: ", reply);
                }
            });
        }

        @Override
        protected void doFlush() throws IOException {
            // Do nothing here
        }

        @Override
        protected void doClose() throws IOException {
            commitOutputMessage();
        }

        @Override
        protected void onWrite() throws IOException {
            // Do nothing here
        }
    }

    protected String getReplyToDestination(Message inMessage) {
        if (inMessage.get(CamelConstants.CAMEL_REBASED_REPLY_TO) != null) {
            return (String) inMessage.get(CamelConstants.CAMEL_REBASED_REPLY_TO);
        }
        else {
            return base.getReplyDestination();
        }
    }

    protected void setReplyCorrelationID(Message inMessage, Exchange reply) {
        Object value = inMessage.get(CamelConstants.CAMEL_CORRELATION_ID);
        if (value != null) {
            reply.getIn().setHeader(CamelConstants.CAMEL_CORRELATION_ID, value);
        }
    }
}
