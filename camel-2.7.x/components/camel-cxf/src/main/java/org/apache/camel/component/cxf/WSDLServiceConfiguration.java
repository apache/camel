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
import javax.wsdl.Service;
import javax.wsdl.WSDLException;
import javax.xml.namespace.QName;

import org.apache.camel.RuntimeCamelException;
import org.apache.cxf.service.factory.AbstractServiceConfiguration;
import org.apache.cxf.wsdl.WSDLManager;

/**
 * This class will help the WSDLServiceFactoryBean to look up the ServiceName and PortName from WSDL
 */
public class WSDLServiceConfiguration extends AbstractServiceConfiguration {
   
    private WSDLServiceFactoryBean serviceFactoryBean;
    private Definition definition;
    private QName serviceQName;
    private QName endpointQName;
    
    public WSDLServiceConfiguration(WSDLServiceFactoryBean factoryBean) {
        this.serviceFactoryBean = factoryBean;
    }
    
    protected Definition getDefinition() {
        if (definition == null) {
            try {
                definition = serviceFactoryBean.getBus().getExtension(WSDLManager.class).getDefinition(serviceFactoryBean.getWsdlURL());
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
        } 
        return definition;
    }
    
    protected QName getServiceQName()  {
        if (serviceQName == null) {
            Map services = getDefinition().getServices();
            if (services.size() == 0) {
                throw new IllegalArgumentException("There is no service in the WSDL" + serviceFactoryBean.getWsdlURL());
            }
            if (services.size() > 1) {
                throw new IllegalArgumentException("service name must be specified, there is more than one service in the WSDL" + serviceFactoryBean.getWsdlURL());
            }
            serviceQName = (QName)services.keySet().iterator().next();
        } 
        return serviceQName;
    }
    
    protected QName getEndpointQName() {
        if (endpointQName == null) {
            Service service = getDefinition().getService(serviceFactoryBean.getServiceQName());
            if (service == null) {
                throw new IllegalArgumentException("There is no service " + serviceFactoryBean.getServiceQName() 
                                                   + " in WSDL " + serviceFactoryBean.getWsdlURL());
            }
            Map ports = service.getPorts();
            if (ports.size() == 0) {
                throw new IllegalArgumentException("There is no port/endpoint in the service "
                                                   + serviceFactoryBean.getServiceQName() + "of WSDL"
                                                   + serviceFactoryBean.getWsdlURL());
            }
            if (ports.size() > 1) {
                throw new IllegalArgumentException("Port/endpoint name must be specified, There is more than one port in the service"
                                                   + serviceFactoryBean.getServiceQName()
                                                   + " of the WSDL" + serviceFactoryBean.getWsdlURL());
            }
            endpointQName = new QName(service.getQName().getNamespaceURI(), (String)ports.keySet().iterator().next());

        }
        return endpointQName;
    }
    
    public String getServiceName() {
        return getServiceQName().getLocalPart();
    }
    
    public String getServiceNamespace() {
        return getServiceQName().getNamespaceURI();
    }
    
    public QName getEndpointName() {
        return getEndpointQName();
    }
    
    
}
