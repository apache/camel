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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.transport.util.CxfMessageHelper;
import org.apache.camel.component.cxf.util.CxfHeaderHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class CamelConduit extends AbstractConduit implements Configurable {
    protected static final String BASE_BEAN_NAME_SUFFIX = ".camel-conduit";
    private static final Logger LOG = LoggerFactory.getLogger(CamelConduit.class);
    // used for places where CXF requires JUL
    private static final java.util.logging.Logger JUL_LOG = LogUtils.getL7dLogger(CamelConduit.class);

    private CamelContext camelContext;
    private EndpointInfo endpointInfo;
    private String targetCamelEndpointUri;
    private Producer producer;
    private ProducerTemplate camelTemplate;
    private Bus bus;
    private HeaderFilterStrategy headerFilterStrategy;

    public CamelConduit(CamelContext context, Bus b, EndpointInfo endpointInfo) {
        this(context, b, endpointInfo, null);
    }

    public CamelConduit(CamelContext context, Bus b, EndpointInfo epInfo, EndpointReferenceType targetReference) {
        this(context, b, epInfo, targetReference, null);
    }

    public CamelConduit(CamelContext context, Bus b, EndpointInfo epInfo, EndpointReferenceType targetReference,
            HeaderFilterStrategy headerFilterStrategy) {
        super(getTargetReference(epInfo, targetReference, b));
        String address = epInfo.getAddress();
        if (address != null) {
            targetCamelEndpointUri = address.substring(CamelTransportConstants.CAMEL_TRANSPORT_PREFIX.length());
            if (targetCamelEndpointUri.startsWith("//")) {
                targetCamelEndpointUri = targetCamelEndpointUri.substring(2);
            }
        }
        camelContext = context;
        endpointInfo = epInfo;
        bus = b;
        initConfig();
        this.headerFilterStrategy = headerFilterStrategy;
        Endpoint target = getCamelContext().getEndpoint(targetCamelEndpointUri);
        try {
            producer = target.createProducer();
            producer.start();
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot create the producer rightly", e);
        }
    }

    public void setCamelContext(CamelContext context) {
        camelContext = context;
    }

    public CamelContext getCamelContext() {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        return camelContext;
    }

    // prepare the message for send out , not actually send out the message
    public void prepare(Message message) throws IOException {
        LOG.trace("CamelConduit send message");
        message.setContent(OutputStream.class, new CamelOutputStream(message));
    }

    public void close() {
        LOG.trace("CamelConduit closed ");
        // shutdown the producer
        try {
            producer.stop();
        } catch (Exception e) {
            LOG.warn("CamelConduit producer stop with the exception", e);
        }
    }

    protected java.util.logging.Logger getLogger() {
        return JUL_LOG;
    }

    public String getBeanName() {
        if (endpointInfo == null || endpointInfo.getName() == null) {
            return "default" + BASE_BEAN_NAME_SUFFIX;
        }
        return endpointInfo.getName().toString() + BASE_BEAN_NAME_SUFFIX;
    }

    private void initConfig() {
        // we could configure the camel context here
        if (bus != null) {
            Configurer configurer = bus.getExtension(Configurer.class);
            if (null != configurer) {
                configurer.configureBean(this);
            }
        }
    }

    @Deprecated
    public ProducerTemplate getCamelTemplate() throws Exception {
        if (camelTemplate == null) {
            camelTemplate = getCamelContext().createProducerTemplate();
        }
        return camelTemplate;
    }

    @Deprecated
    public void setCamelTemplate(ProducerTemplate template) {
        camelTemplate = template;
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
        }

        protected void onWrite() throws IOException {
            // do nothing here
        }


        private void commitOutputMessage() throws IOException {
            ExchangePattern pattern;
            if (isOneWay) {
                pattern = ExchangePattern.InOnly;
            } else {
                pattern = ExchangePattern.InOut;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("send the message to endpoint" + targetCamelEndpointUri);
            }
            org.apache.camel.Exchange exchange = producer.createExchange(pattern);

            exchange.setProperty(Exchange.TO_ENDPOINT, targetCamelEndpointUri);
            CachedOutputStream outputStream = (CachedOutputStream) outMessage.getContent(OutputStream.class);
            // Send out the request message here, copy the protocolHeader back
            CxfHeaderHelper.propagateCxfToCamel(headerFilterStrategy, outMessage, exchange.getIn().getHeaders(), exchange);

            // TODO support different encoding
            exchange.getIn().setBody(outputStream.getInputStream());
            LOG.debug("template sending request: ", exchange.getIn());
            Exception exception;
            try {
                producer.process(exchange);
            } catch (Exception ex) {
                exception = ex;
            }
            // Throw the exception that the template get
            exception = exchange.getException();
            if (exception != null) {
                throw new IOException("Cannot send the request message.", exchange.getException());
            }
            exchange.setProperty(CamelTransportConstants.CXF_EXCHANGE, outMessage.getExchange());
            if (!isOneWay) {
                handleResponse(exchange);
            }

        }

        private void handleResponse(org.apache.camel.Exchange exchange) throws IOException {
            org.apache.cxf.message.Message inMessage = null;
            try {
                inMessage = CxfMessageHelper.getCxfInMessage(headerFilterStrategy, exchange, true);
            } catch (Exception ex) {
                throw new IOException("Cannot get the response message. ", ex);
            }
            incomingObserver.onMessage(inMessage);
        }
    }

    /**
     * Represented decoupled response endpoint.
     */
    // TODO: This class is not used
    @Deprecated
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
        }

        public synchronized void setMessageObserver(MessageObserver observer) {
            decoupledMessageObserver = observer;
        }

        public synchronized MessageObserver getMessageObserver() {
            return decoupledMessageObserver;
        }
    }

}
