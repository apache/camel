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

import javax.xml.namespace.QName;
import javax.xml.ws.Provider;

import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.bus.spring.BusWiringBeanFactoryPostProcessor;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Endpoint</a>
 */
public class CxfSpringEndpoint extends CxfEndpoint implements ApplicationContextAware {

    private String beanId;
    private ApplicationContext applicationContext;
    
    public CxfSpringEndpoint(CxfComponent component, String address) throws Exception {
        super(address, component);
    }
    
    public CxfSpringEndpoint() {
    }

    // Package private methods
    // -------------------------------------------------------------------------

    /**
     * Create a CXF Client
     */
    @Override
    Client createClient() throws Exception {
        
        // get service class
        Class<?> cls = getServiceClass();    
        
        if (getDataFormat().equals(DataFormat.POJO)) { 
            ObjectHelper.notNull(cls, CxfConstants.SERVICE_CLASS);
        }

        if (getWsdlURL() == null && cls == null) {
            // no WSDL and serviceClass specified, set our default serviceClass
            setServiceClass(org.apache.camel.component.cxf.DefaultSEI.class.getName());
            setDefaultOperationNamespace(CxfConstants.DISPATCH_NAMESPACE);
            setDefaultOperationName(CxfConstants.DISPATCH_DEFAULT_OPERATION_NAMESPACE);               
            if (getDataFormat().equals(DataFormat.PAYLOAD)) { 
                setSkipPayloadMessagePartCheck(true);
            }
            cls = getServiceClass();
        }
        
        ClientFactoryBean factoryBean;
        if (cls != null) {
            // create client factory bean
            factoryBean = createClientFactoryBean(cls);
        } else {
            factoryBean = createClientFactoryBean();
        }

        // setup client factory bean
        setupClientFactoryBean(factoryBean, cls);

        // fill in values that have not been filled.
        QName serviceQName = null;
        try {
            serviceQName = factoryBean.getServiceName();
        } catch (IllegalStateException e) {
            // It throws IllegalStateException if serviceName has not been set.
        }

        if (serviceQName == null && getServiceLocalName() != null) {
            factoryBean.setServiceName(new QName(getServiceNamespace(), getServiceLocalName()));
        }
        if (factoryBean.getEndpointName() == null && getEndpointLocalName() != null) {
            factoryBean.setEndpointName(new QName(getEndpointNamespace(), getEndpointLocalName()));
        }
        
        if (cls == null) {
            checkName(factoryBean.getEndpointName(), "endpoint/port name");
            checkName(factoryBean.getServiceName(), "service name");
        }

        Client client = factoryBean.create();
        // setup the handlers
        setupHandlers(factoryBean, client);
        return client;
    }


    /**
     * Create a service factory bean
     */
    @Override
    ServerFactoryBean createServerFactoryBean() throws Exception  {
        
        // get service class
        Class<?> cls = getServiceClass();                
        
        if (getWsdlURL() == null && cls == null) {
            // no WSDL and serviceClass specified, set our default serviceClass
            if (getDataFormat().equals(DataFormat.PAYLOAD)) {
                setServiceClass(org.apache.camel.component.cxf.DefaultPayloadProviderSEI.class.getName());
            } 
            cls = getServiceClass();
        }
        
        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;

        if (cls == null) {
            if (!getDataFormat().equals(DataFormat.POJO)) {
                answer = new JaxWsServerFactoryBean(new WSDLServiceFactoryBean());
                cls = Provider.class;
            } else {
                ObjectHelper.notNull(cls, CxfConstants.SERVICE_CLASS);
            }
        } else if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            answer = new JaxWsServerFactoryBean();
        } else {
            answer = new ServerFactoryBean();
        }

        // setup server factory bean
        setupServerFactoryBean(answer, cls);

        // fill in values that have not been filled.
        if (answer.getServiceName() == null && getServiceLocalName() != null) {
            answer.setServiceName(new QName(getServiceNamespace(), getServiceLocalName()));
        }
        if (answer.getEndpointName() == null && getEndpointLocalName() != null) {
            answer.setEndpointName(new QName(getEndpointNamespace(), getEndpointLocalName()));
        }

        if (cls == null) {
            checkName(answer.getEndpointName(), "endpoint/port name");
            checkName(answer.getServiceName(), "service name");
        }
        return answer;
    }

    // Properties
    // -------------------------------------------------------------------------
    @Override
    public String getBeanId() {
        return beanId;
    }
    
    // this property will be set by spring
    @Override
    public void setBeanId(String id) {
        this.beanId = id;
    }
    
    public void setServiceNamespace(String serviceNamespace) {
        QName qn = getServiceNameAsQName();
        if (qn == null) {
            setServiceNameAsQName(new QName(serviceNamespace, "local"));
        } else {
            setServiceNameAsQName(new QName(serviceNamespace, qn.getLocalPart()));
        }
    }

    public String getServiceNamespace() {
        QName qn = getServiceNameAsQName();
        if (qn == null) {
            return null;
        }
        return qn.getNamespaceURI();
    }

    public void setServiceLocalName(String serviceLocalName) {
        QName qn = getServiceNameAsQName();
        if (qn == null) {
            setServiceNameAsQName(new QName("", serviceLocalName));
        } else {
            setServiceNameAsQName(new QName(qn.getNamespaceURI(), serviceLocalName));
        }
    }

    public String getServiceLocalName() {
        QName qn = getServiceNameAsQName();
        if (qn == null) {
            return null;
        }
        return qn.getLocalPart();
    }

    public String getEndpointLocalName() {
        QName qn = getPortNameAsQName();
        if (qn == null) {
            return null;
        }
        return qn.getLocalPart();
    }

    public void setEndpointLocalName(String endpointLocalName) {
        QName qn = getPortNameAsQName();
        if (qn == null) {
            setPortNameAsQName(new QName("", endpointLocalName));
        } else {
            setPortNameAsQName(new QName(qn.getNamespaceURI(), endpointLocalName));
        }
    }

    public void setEndpointNamespace(String endpointNamespace) {
        QName qn = getPortNameAsQName();
        if (qn == null) {
            setPortNameAsQName(new QName(endpointNamespace, "local"));
        } else {
            setPortNameAsQName(new QName(endpointNamespace, qn.getLocalPart()));
        }
    }

    public String getEndpointNamespace() {
        QName qn = getPortNameAsQName();
        if (qn == null) {
            return null;
        }
        return qn.getNamespaceURI();
    }
    
    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;

        if (bus == null) {
            createBus = true;
            bus = BusWiringBeanFactoryPostProcessor.addDefaultBus(ctx);
        }
    }
    
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
