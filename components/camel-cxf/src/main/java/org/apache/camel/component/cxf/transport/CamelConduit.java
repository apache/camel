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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.CxfSoapBinding;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

/**
 * @version $Revision$
 */
public class CamelConduit extends AbstractConduit implements Configurable {
    protected static final String BASE_BEAN_NAME_SUFFIX = ".camel-conduit";
    private static final Logger LOG = LogUtils.getL7dLogger(CamelConduit.class);
    private CamelContext camelContext;
    private EndpointInfo endpointInfo;
    private String targetCamelEndpointUri;
    private ProducerTemplate<Exchange> camelTemplate;
    private Bus bus;

    public CamelConduit(CamelContext context, Bus b, EndpointInfo endpointInfo) {
        this(context, b, endpointInfo, null);
    }

    public CamelConduit(CamelContext context, Bus b, EndpointInfo epInfo, EndpointReferenceType targetReference) {
        super(targetReference);
        String address = epInfo.getAddress();
        if (address != null) {
            targetCamelEndpointUri = address.substring(CxfConstants.CAMEL_TRANSPORT_PREFIX.length());
            if (targetCamelEndpointUri.startsWith("//")) {
                targetCamelEndpointUri = targetCamelEndpointUri.substring(2);
            }
        }
        camelContext = context;
        endpointInfo = epInfo;
        bus = b;
        initConfig();
    }

    public void setCamelContext(CamelContext context) {
        camelContext = context;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    // prepare the message for send out , not actually send out the message
    public void prepare(Message message) throws IOException {
        getLogger().log(Level.FINE, "CamelConduit send message");
        message.setContent(OutputStream.class, new CamelOutputStream(message));
    }

    public void close() {
        getLogger().log(Level.FINE, "CamelConduit closed ");

    }

    protected Logger getLogger() {
        return LOG;
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

    public ProducerTemplate<Exchange> getCamelTemplate() {
        if (camelTemplate == null) {
            CamelContext ctx = camelContext != null ? camelContext : new DefaultCamelContext();
            camelTemplate = ctx.createProducerTemplate();
        }
        return camelTemplate;
    }

    public void setCamelTemplate(ProducerTemplate<Exchange> template) {
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


        private void commitOutputMessage() {
            ExchangePattern pattern;
            if (isOneWay) {
                pattern = ExchangePattern.InOnly;
            } else {
                pattern = ExchangePattern.InOut;
            }
            getLogger().log(Level.FINE, "send the message to endpoint" + targetCamelEndpointUri);
            // We could wait for the rely asynchronously
            org.apache.camel.Exchange exchange = getCamelTemplate().send(targetCamelEndpointUri, pattern, new Processor() {
                public void process(org.apache.camel.Exchange ex) throws IOException {
                    CachedOutputStream outputStream = (CachedOutputStream)outMessage.getContent(OutputStream.class);
                    // Send out the request message here, copy the protocolHeader back
                    Map<String, List<String>> protocolHeader = CastUtils.cast((Map<?, ?>)outMessage.get(Message.PROTOCOL_HEADERS));
                    CxfSoapBinding.setProtocolHeader(ex.getIn().getHeaders(), protocolHeader);
                    ex.getIn().setBody(outputStream.getBytes());
                    getLogger().log(Level.FINE, "template sending request: ", ex.getIn());
                }
            });
            exchange.setProperty(CxfConstants.CXF_EXCHANGE, outMessage.getExchange());
            if (!isOneWay) {
                handleResponse(exchange);
            }

        }

        private void handleResponse(org.apache.camel.Exchange exchange) {
            org.apache.cxf.message.Message inMessage = CxfSoapBinding.getCxfInMessage(exchange, true);
            incomingObserver.onMessage(inMessage);
        }
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
        }

        public synchronized void setMessageObserver(MessageObserver observer) {
            decoupledMessageObserver = observer;
        }

        public synchronized MessageObserver getMessageObserver() {
            return decoupledMessageObserver;
        }
    }

}
