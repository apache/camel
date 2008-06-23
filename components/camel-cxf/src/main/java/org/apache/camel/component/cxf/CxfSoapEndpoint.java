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
package org.apache.camel.component.cxf;

import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.helpers.DOMUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * A CXF based SOAP endpoint which wraps an existing
 * endpoint with SOAP processing.
 */
public class CxfSoapEndpoint implements Endpoint {

    private final Endpoint endpoint;
    private Resource wsdl;
    private String serviceClass;
    private org.w3c.dom.Document description;
    private Definition definition;
    private QName serviceName;
    private QName endpointName;
    private Bus bus;


    public CxfSoapEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    protected Endpoint getInnerEndpoint() {
        return endpoint;
    }

    public boolean isSingleton() {
        return endpoint.isSingleton();
    }

    public String getEndpointUri() {
        return endpoint.getEndpointUri();
    }

    public Exchange createExchange() {
        return endpoint.createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return endpoint.createExchange(pattern);
    }

    public Exchange createExchange(Exchange exchange) {
        return endpoint.createExchange(exchange);
    }

    public CamelContext getCamelContext() {
        return endpoint.getCamelContext();
    }

    public Producer createProducer() throws Exception {
        return new CxfSoapProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CxfSoapConsumer(this, processor);
    }

    public PollingConsumer createPollingConsumer() throws Exception {
        throw new UnsupportedOperationException();
    }

    public void configureProperties(Map options) {
    }

    public Resource getWsdl() {
        return wsdl;
    }

    public void setWsdl(Resource wsdl) {
        this.wsdl = wsdl;
    }

    public void setServiceClass(String serviceClass) {
        this.serviceClass = serviceClass;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = QName.valueOf(serviceName);
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = QName.valueOf(endpointName);
    }

    public QName getEndpointName() {
        return endpointName;
    }

    public void init() throws Exception {
        Assert.notNull(wsdl, "soap.wsdl parameter must be set on the uri");
        if (serviceName == null) {
            description = DOMUtils.readXml(wsdl.getInputStream());
            WSDLFactory wsdlFactory = WSDLFactory.newInstance();
            WSDLReader reader = wsdlFactory.newWSDLReader();
            reader.setFeature("javax.wsdl.verbose", false);
            definition = reader.readWSDL(wsdl.getURL().toString(), description);
            serviceName = (QName) definition.getServices().keySet().iterator().next();
        }
    }

    protected Bus getBus() {
        if (bus == null) {
            bus = BusFactory.newInstance().createBus();
        }
        return bus;
    }

    public Definition getDefinition() {
        return definition;
    }

    public QName getServiceName() {
        return serviceName;
    }

    public void setCamelContext(CamelContext context) {
        endpoint.setCamelContext(context);
    }
    
    @Deprecated
    public CamelContext getContext() {
        return getCamelContext();
    }
    
    @Deprecated
    public void setContext(CamelContext context) {
        setCamelContext(context);
    }
}
