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
package org.apache.camel.component.cxf;

import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.Service;
import javax.xml.namespace.QName;
import javax.xml.ws.Provider;

import org.apache.camel.RuntimeCamelException;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointException;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxws.support.JaxWsEndpointImpl;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.service.factory.FactoryBeanListener.Event;
import org.apache.cxf.service.invoker.Invoker;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.wsdl.WSDLManager;

/**
 * A service factory bean class that create a service factory without requiring a service class
 * (SEI).
 * It will pick the first one service name and first one port/endpoint name in the WSDL, if 
 * there is service name or port/endpoint name setted.
 */
public class WSDLServiceFactoryBean extends JaxWsServiceFactoryBean {
    
    private Definition definition;

    public WSDLServiceFactoryBean() {
        setServiceClass(Provider.class);
    }
    
    public WSDLServiceFactoryBean(Class<?> serviceClass) {
        setServiceClass(serviceClass);
    }
    
    @Override
    public void setServiceClass(Class<?> serviceClass) {
        if (serviceClass != null) {
            super.setServiceClass(serviceClass);
        }
    }
    protected Definition getDefinition(String url) {
        if (definition == null) {
            try {
                definition = getBus().getExtension(WSDLManager.class).getDefinition(url);
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
        } 
        
        if (this.getServiceQName(false) == null) {
            Map<QName, ?> services = CastUtils.cast(definition.getServices());
            if (services.size() == 0) {
                throw new IllegalArgumentException("There is no service in the WSDL" + url);
            }
            if (services.size() > 1) {
                throw new IllegalArgumentException("service name must be specified, there is more than one service in the WSDL" + url);
            }
            QName serviceQName = services.keySet().iterator().next();
            this.setServiceName(serviceQName);
        }

        if (this.getEndpointName(false) == null) {
            Service service = definition.getService(getServiceQName(false));
            Map<String, ?> ports = CastUtils.cast(service.getPorts());
            if (ports.size() == 0) {
                throw new IllegalArgumentException("There is no port/endpoint in the service "
                                                   + getServiceQName() + "of WSDL"
                                                   + url);
            }
            if (ports.size() > 1) {
                throw new IllegalArgumentException("Port/endpoint name must be specified, There is more than one port in the service"
                                                   + service.getQName()
                                                   + " of the WSDL" + url);
            }
            QName endpointQName = new QName(service.getQName().getNamespaceURI(), ports.keySet().iterator().next());
            setEndpointName(endpointQName);
        }
        return definition;
    }
    @Override
    protected void buildServiceFromWSDL(String url) {
        getDefinition(url);
        super.buildServiceFromWSDL(url);
    }
    @Override
    public Endpoint createEndpoint(EndpointInfo ei) throws EndpointException {
        Endpoint ep = new JaxWsEndpointImpl(getBus(), getService(), ei);
        sendEvent(Event.ENDPOINT_CREATED, ei, ep, getServiceClass());
        return ep;
    }

    @Override
    protected void initializeWSDLOperations() {
        // skip this operation that requires service class
    }
    
    @Override
    protected void checkServiceClassAnnotations(Class<?> sc) {
        // skip this operation that requires service class
    }
    
    @Override
    protected Invoker createInvoker() {
        // Camel specific invoker will be set 
        return null;
    }

}
