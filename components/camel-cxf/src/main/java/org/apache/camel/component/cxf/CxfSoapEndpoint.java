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

import org.apache.camel.*;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.helpers.DOMUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import javax.wsdl.Definition;
import javax.wsdl.xml.WSDLReader;
import javax.wsdl.factory.WSDLFactory;
import javax.xml.namespace.QName;

/**
 * A CXF based SOAP endpoint which wraps an existing
 * endpoint with SOAP processing.
 */
public class CxfSoapEndpoint implements Endpoint {

    private final Endpoint endpoint;
    private Resource wsdl;
    private org.w3c.dom.Document description;
    private Definition definition;
    private QName service;
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

    public CamelContext getContext() {
        return endpoint.getContext();
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

    public void setWsdl(Resource wsdl) {
        this.wsdl = wsdl;
    }

    public void init() throws Exception {
        Assert.notNull(wsdl, "soap.wsdl parameter must be set on the uri");
        description = DOMUtils.readXml(wsdl.getInputStream());
        WSDLFactory wsdlFactory = WSDLFactory.newInstance();
        WSDLReader reader = wsdlFactory.newWSDLReader();
        reader.setFeature("javax.wsdl.verbose", false);
        definition = reader.readWSDL(wsdl.getURL().toString(), description);
        service = (QName) definition.getServices().keySet().iterator().next();
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

    public QName getService() {
        return service;
    }
}
