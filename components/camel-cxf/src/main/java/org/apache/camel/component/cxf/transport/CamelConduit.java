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
package org.apache.camel.component.cxf.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

/**
 * @version $Revision$
 */
public class CamelConduit extends AbstractConduit implements Configurable {
    protected static final String BASE_BEAN_NAME_SUFFIX = ".camel-conduit-base";
    private static final Logger LOG = LogUtils.getL7dLogger(CamelConduit.class);
    private final CamelTransportBase base;
    private String targetCamelEndpointUri;

    /*
     * protected ClientConfig clientConfig; protected ClientBehaviorPolicyType
     * runtimePolicy; protected AddressType address; protected SessionPoolType
     * sessionPool;
     */

    public CamelConduit(CamelContext camelContext, Bus bus, EndpointInfo endpointInfo, EndpointReferenceType targetReference) {
        super(targetReference);
        AttributedURIType address = targetReference.getAddress();
        if (address != null) {
            this.targetCamelEndpointUri = address.getValue();
        }

        base = new CamelTransportBase(camelContext, bus, endpointInfo, false, BASE_BEAN_NAME_SUFFIX);

        initConfig();
    }

    // prepare the message for send out , not actually send out the message
    public void prepare(Message message) throws IOException {
        getLogger().log(Level.FINE, "CamelConduit send message");

        message.setContent(OutputStream.class, new CamelOutputStream(message));
    }

    public void close() {
        getLogger().log(Level.FINE, "CamelConduit closed ");

        // ensure resources held by session factory are released
        //
        base.close();
    }

    protected Logger getLogger() {
        return LOG;
    }

    public String getBeanName() {
        EndpointInfo info = base.endpointInfo;
        if (info == null) {
            return "default.camel-conduit";
        }
        return info.getName() + ".camel-conduit";
    }

    private void initConfig() {

        /*
         * this.address = base.endpointInfo.getTraversedExtensor(new
         * AddressType(), AddressType.class); this.sessionPool =
         * base.endpointInfo.getTraversedExtensor(new SessionPoolType(),
         * SessionPoolType.class); this.clientConfig =
         * base.endpointInfo.getTraversedExtensor(new ClientConfig(),
         * ClientConfig.class); this.runtimePolicy =
         * base.endpointInfo.getTraversedExtensor(new
         * ClientBehaviorPolicyType(), ClientBehaviorPolicyType.class);
         */

        Configurer configurer = base.bus.getExtension(Configurer.class);
        if (null != configurer) {
            configurer.configureBean(this);
        }
    }

    private class CamelOutputStream extends CachedOutputStream {
        private Message outMessage;
        private boolean isOneWay;

        public CamelOutputStream(Message m) {
            outMessage = m;
        }

        protected void doFlush() throws IOException {
            // do nothing here
        }

        protected void doClose() throws IOException {
            isOneWay = outMessage.getExchange().isOneWay();
            commitOutputMessage();
            if (!isOneWay) {
                handleResponse();
            }
        }

        protected void onWrite() throws IOException {

        }

        private void commitOutputMessage() {
            base.template.send(targetCamelEndpointUri, new Processor() {
                public void process(org.apache.camel.Exchange reply) {
                    Object request = null;
                    if (isTextPayload()) {
                        request = currentStream.toString();
                    } else {
                        request = ((ByteArrayOutputStream)currentStream).toByteArray();
                    }

                    getLogger().log(Level.FINE, "Conduit Request is :[" + request + "]");
                    String replyTo = base.getReplyDestination();
                    // TODO setting up the responseExpected
                    base.marshal(request, replyTo, reply);
                    base.setMessageProperties(outMessage, reply);

                    String correlationID = null;
                    if (!isOneWay) {
                        // TODO create a correlationID
                        String id = null;

                        if (id != null) {
                            if (correlationID != null) {
                                String error = "User cannot set CamelCorrelationID when " + "making a request/reply invocation using " + "a static replyTo Queue.";
                            }
                            correlationID = id;
                        }
                    }

                    if (correlationID != null) {
                        reply.getIn().setHeader(CamelConstants.CAMEL_CORRELATION_ID, correlationID);
                    } else {
                        // No message correlation id is set. Whatever comeback
                        // will be accepted as responses.
                        // We assume that it will only happen in case of the
                        // temp. reply queue.
                    }

                    getLogger().log(Level.FINE, "template sending request: ", reply.getIn());
                }
            });
        }

        private void handleResponse() throws IOException {
            // REVISIT distinguish decoupled case or oneway call
            Object response = null;

            // TODO if outMessage need to get the response
            Message inMessage = new MessageImpl();
            outMessage.getExchange().setInMessage(inMessage);
            // set the message header back to the incomeMessage
            // inMessage.put(CamelConstants.CAMEL_CLIENT_RESPONSE_HEADERS,
            // outMessage.get(CamelConstants.CAMEL_CLIENT_RESPONSE_HEADERS));

            /*
             * Object result1; Object result = null; javax.camel.Message
             * camelMessage1 = pooledSession.consumer().receive(timeout);
             * getLogger().log(Level.FINE, "template received reply: " ,
             * camelMessage1); if (camelMessage1 != null) {
             * base.populateIncomingContext(camelMessage1, outMessage,
             * CamelConstants.CAMEL_CLIENT_RESPONSE_HEADERS); String messageType =
             * camelMessage1 instanceof TextMessage ?
             * CamelConstants.TEXT_MESSAGE_TYPE :
             * CamelConstants.BINARY_MESSAGE_TYPE; result =
             * base.unmarshal((org.apache.camel.Exchange) outMessage); result1 =
             * result; } else { String error = "CamelClientTransport.receive()
             * timed out. No message available."; getLogger().log(Level.SEVERE,
             * error); //TODO: Review what exception should we throw. throw new
             * CamelException(error); } response = result1; //set the message
             * header back to the incomeMessage
             * inMessage.put(CamelConstants.CAMEL_CLIENT_RESPONSE_HEADERS,
             * outMessage.get(CamelConstants.CAMEL_CLIENT_RESPONSE_HEADERS));
             */

            getLogger().log(Level.FINE, "The Response Message is : [" + response + "]");

            // setup the inMessage response stream
            byte[] bytes = null;
            if (response instanceof String) {
                String requestString = (String)response;
                bytes = requestString.getBytes();
            } else {
                bytes = (byte[])response;
            }
            inMessage.setContent(InputStream.class, new ByteArrayInputStream(bytes));
            getLogger().log(Level.FINE, "incoming observer is " + incomingObserver);
            incomingObserver.onMessage(inMessage);
        }
    }

    private boolean isTextPayload() {
        // TODO use runtime policy
        return true;
    }

    /**
     * Represented decoupled response endpoint.
     */
    protected class DecoupledDestination implements Destination {
        protected MessageObserver decoupledMessageObserver;
        private EndpointReferenceType address;

        DecoupledDestination(EndpointReferenceType ref, MessageObserver incomingObserver) {
            address = ref;
            decoupledMessageObserver = incomingObserver;
        }

        public EndpointReferenceType getAddress() {
            return address;
        }

        public Conduit getBackChannel(Message inMessage, Message partialResponse, EndpointReferenceType addr) throws IOException {
            // shouldn't be called on decoupled endpoint
            return null;
        }

        public void shutdown() {
            // TODO Auto-generated method stub
        }

        public synchronized void setMessageObserver(MessageObserver observer) {
            decoupledMessageObserver = observer;
        }

        public synchronized MessageObserver getMessageObserver() {
            return decoupledMessageObserver;
        }
    }

}
