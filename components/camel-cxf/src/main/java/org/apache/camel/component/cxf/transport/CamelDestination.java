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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelTemplate;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.CxfSoapBinding;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.wsdl.EndpointReferenceUtils;

/**
 * @version $Revision$
 */
public class CamelDestination extends AbstractDestination implements Configurable {
    protected static final String BASE_BEAN_NAME_SUFFIX = ".camel-destination";
    private static final Logger LOG = LogUtils.getL7dLogger(CamelDestination.class);
    private CamelTemplate<Exchange> camelTemplate; 
    CamelContext camelContext;
    Consumer consumer;
    String camelUri;
    final ConduitInitiator conduitInitiator;
    
    private org.apache.camel.Endpoint distinationEndpoint;

    public CamelDestination(CamelContext camelContext, Bus bus, ConduitInitiator ci, EndpointInfo info) throws IOException {
        super(getTargetReference(info, bus), info);
        this.camelContext = camelContext;
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
        return new BackChannelConduit(EndpointReferenceUtils.getAnonymousEndpointReference(), inMessage);
    }

    public void activate() {
        getLogger().log(Level.INFO, "CamelDestination activate().... ");

        try {
            getLogger().log(Level.FINE, "establishing Camel connection");
            distinationEndpoint = camelContext.getEndpoint(camelUri);
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

    protected void incoming(org.apache.camel.Exchange exchange) {
        getLogger().log(Level.FINE, "server received request: ", exchange);
        org.apache.cxf.message.Message inMessage = 
            CxfSoapBinding.getCxfInMessage(exchange, false);
        
        inMessage.put(CamelConstants.CAMEL_REQUEST_MESSAGE, exchange);

        ((MessageImpl)inMessage).setDestination(this);

        // handle the incoming message
        incomingObserver.onMessage(inMessage);
    }

    public String getBeanName() {
        return endpointInfo.getName().toString() + ".camel-destination";
    }

    private void initConfig() {
        // setup the endpoint infor here
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
        String targetCamelEndpointUri;
        BackChannelConduit(EndpointReferenceType ref, Message message) {
            super(ref);
            AttributedURIType address = ref.getAddress();
            if (address != null) {
                targetCamelEndpointUri = address.getValue();
            }
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
            message.put(CamelConstants.CAMEL_REQUEST_MESSAGE, inMessage.get(CamelConstants.CAMEL_REQUEST_MESSAGE));
            message.put(CamelConstants.CAMEL_TARGET_ENDPOINT_URI, targetCamelEndpointUri);
            message.setContent(OutputStream.class, new CamelOutputStream(inMessage));
        }

        protected Logger getLogger() {
            return LOG;
        }

    }

    private class CamelOutputStream extends CachedOutputStream {
        private Message outMessage;      
        
        public CamelOutputStream(Message m) {
            super();
            outMessage = m;
        }

        // prepair the message and get the send out message
        private void commitOutputMessage() throws IOException {
            String targetCamelEndpointUri = (String) outMessage.get(CamelConstants.CAMEL_TARGET_ENDPOINT_URI);  
            getCamelTemplate().send(targetCamelEndpointUri, new Processor() {
                public void process(org.apache.camel.Exchange ex) throws IOException {
                    // put the output stream into the message
                    CachedOutputStream outputStream = (CachedOutputStream)outMessage.getContent(OutputStream.class);
                    // send out the request message here
                    ex.getIn().setHeaders(outMessage);
                    ex.getIn().setBody(outputStream.getInputStream());
                    // setup the out message
                    getLogger().log(Level.FINE, "template sending request: ", ex.getIn());
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
  
}
