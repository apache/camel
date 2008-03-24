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

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelTemplate;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.CxfSoapBinding;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.io.CachedOutputStream;
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

/**
 * @version $Revision$
 */
public class CamelDestination extends AbstractDestination implements Configurable {
    protected static final String BASE_BEAN_NAME_SUFFIX = ".camel-destination";
    private static final Logger LOG = LogUtils.getL7dLogger(CamelDestination.class);
    final ConduitInitiator conduitInitiator;
    CamelContext camelContext;
    Consumer consumer;
    String camelDestinationUri;
    private CamelTemplate<Exchange> camelTemplate;
    private org.apache.camel.Endpoint distinationEndpoint;

    public CamelDestination(CamelContext camelContext, Bus bus, ConduitInitiator ci, EndpointInfo info) throws IOException {
        super(bus, getTargetReference(info, bus), info);
        this.camelContext = camelContext;
        conduitInitiator = ci;
        camelDestinationUri = endpointInfo.getAddress().substring(CxfConstants.CAMEL_TRANSPORT_PREFIX.length());
        if (camelDestinationUri.startsWith("//")) {
            camelDestinationUri = camelDestinationUri.substring(2);
        }
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
        //we can pass the message back by looking up the camelExchange from inMessage
        return new BackChannelConduit(inMessage);
    }

    public void activate() {
        getLogger().log(Level.FINE, "CamelDestination activate().... ");

        try {
            getLogger().log(Level.FINE, "establishing Camel connection");
            distinationEndpoint = camelContext.getEndpoint(camelDestinationUri);
            consumer = distinationEndpoint.createConsumer(new ConsumerProcessor());
            consumer.start();

        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Camel connect failed with EException : ", ex);
        }
    }

    public void deactivate() {
        try {
            consumer.stop();
        } catch (Exception e) {
            // TODO need to handle the exception somewhere
            e.printStackTrace();
        }
    }

    public void shutdown() {
        getLogger().log(Level.FINE, "CamelDestination shutdown()");
        this.deactivate();
    }

    public CamelTemplate getCamelTemplate() {
        if (camelTemplate == null) {
            if (camelContext != null) {
                camelTemplate = new CamelTemplate<Exchange>(camelContext);
            } else {
                camelTemplate = new CamelTemplate<Exchange>(new DefaultCamelContext());
            }
        }
        return camelTemplate;
    }

    public void setCamelTemplate(CamelTemplate<Exchange> template) {
        camelTemplate = template;
    }

    public void setCamelContext(CamelContext context) {
        camelContext = context;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    protected void incoming(org.apache.camel.Exchange camelExchange) {
        getLogger().log(Level.FINE, "server received request: ", camelExchange);
        org.apache.cxf.message.Message inMessage =
            CxfSoapBinding.getCxfInMessage(camelExchange, false);

        inMessage.put(CxfConstants.CAMEL_EXCHANGE, camelExchange);
        ((MessageImpl)inMessage).setDestination(this);

        // Handling the incoming message
        // The response message will be send back by the outgoingchain
        incomingObserver.onMessage(inMessage);

    }

    public String getBeanName() {
        if (endpointInfo == null || endpointInfo.getName() == null) {
            return "default" + BASE_BEAN_NAME_SUFFIX;
        }
        return endpointInfo.getName().toString() + BASE_BEAN_NAME_SUFFIX;
    }

    private void initConfig() {
        //we could configure the camel context here
        if (bus != null) {
            Configurer configurer = bus.getExtension(Configurer.class);
            if (null != configurer) {
                configurer.configureBean(this);
            }
        }
    }

    protected class ConsumerProcessor implements Processor {
        public void process(Exchange exchange) {
            try {
                incoming(exchange);
            } catch (Throwable ex) {
                getLogger().log(Level.WARNING, "Failed to process incoming message : ", ex);
            }
        }
    }

    // this should deal with the cxf message
    protected class BackChannelConduit extends AbstractConduit {
        protected Message inMessage;
        Exchange camelExchange;
        org.apache.cxf.message.Exchange cxfExchange;
        BackChannelConduit(Message message) {
            super(EndpointReferenceUtils.getAnonymousEndpointReference());
            inMessage = message;
            cxfExchange = inMessage.getExchange();
            camelExchange = cxfExchange.get(Exchange.class);
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
            message.put(CxfConstants.CAMEL_EXCHANGE, inMessage.get(CxfConstants.CAMEL_EXCHANGE));
            message.setContent(OutputStream.class, new CamelOutputStream(message));

        }

        protected Logger getLogger() {
            return LOG;
        }

    }

    /**
     * Mark message as a partial message.
     *
     * @param partialResponse the partial response message
     * @param the decoupled target
     * @return true iff partial responses are supported
     */
    protected boolean markPartialResponse(Message partialResponse,
                                       EndpointReferenceType decoupledTarget) {
        return true;
    }

    /**
     * @return the associated conduit initiator
     */
    protected ConduitInitiator getConduitInitiator() {
        return conduitInitiator;
    }


    private class CamelOutputStream extends CachedOutputStream {
        private Message outMessage;

        public CamelOutputStream(Message m) {
            super();
            outMessage = m;
        }

        // prepair the message and get the send out message
        private void commitOutputMessage() throws IOException {
            Exchange camelExchange = (Exchange)outMessage.get(CxfConstants.CAMEL_EXCHANGE);
            camelExchange.getOut().setHeaders(outMessage);
            CachedOutputStream outputStream = (CachedOutputStream)outMessage.getContent(OutputStream.class);
            camelExchange.getOut().setBody(outputStream.getInputStream());
            getLogger().log(Level.FINE, "send the response message: " + outputStream);

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

}
