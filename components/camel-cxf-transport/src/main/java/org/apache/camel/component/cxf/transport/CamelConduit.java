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
package org.apache.camel.component.cxf.transport;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurable;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    @Override
    public void prepare(Message message) throws IOException {
        LOG.trace("CamelConduit send message");
        CamelOutputStream os = new CamelOutputStream(this.targetCamelEndpointUri, 
                                                     this.producer, 
                                                     this.headerFilterStrategy, 
                                                     this.getMessageObserver(), 
                                                     message);
        message.setContent(OutputStream.class, os);
    }

    @Override
    public void close() {
        LOG.trace("CamelConduit closed ");
        // shutdown the producer
        try {
            producer.stop();
        } catch (Exception e) {
            LOG.warn("CamelConduit producer stop with the exception", e);
        }
    }

    @Override
    protected java.util.logging.Logger getLogger() {
        return JUL_LOG;
    }

    @Override
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

}
