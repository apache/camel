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

import java.lang.reflect.Proxy;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.BusWiringBeanFactoryPostProcessor;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.version.Version;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Endpoint</a>
 *
 * @version 
 */
public class CxfSpringEndpoint extends CxfEndpoint implements ApplicationContextAware {

    private String beanId;
    private ConfigurerImpl configurer;
    private ApplicationContext applicationContext;

    public CxfSpringEndpoint(CamelContext context, String address) throws Exception {
        super(address, context);
    }
    
    public CxfSpringEndpoint(CxfComponent component, String address) throws Exception {
        super(address, component);
    }
    
    public CxfSpringEndpoint() {
        super();
    }

    /**
     * 
     * A help to get the service class.  The serviceClass classname in URI 
     * query takes precedence over the serviceClass in CxfEndpointBean.
     */
    private Class<?> getSEIClass() throws ClassNotFoundException {
        
        // get service class
        Class<?> answer = null;
        if (getServiceClass() != null) {
            // classname is specified in URI which overrides the bean properties
            answer = getServiceClass();
        }
        return answer;
    }
    

    // Package private methods
    // -------------------------------------------------------------------------

    /**
     * Create a CXF Client
     */
    @Override
    Client createClient() throws Exception {
        
        // get service class
        Class<?> cls = getSEIClass();    
        
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
            cls = getSEIClass();
        }
        
        if (cls != null) {
            // create client factory bean
            ClientProxyFactoryBean factoryBean = createClientFactoryBean(cls);

            // configure client factory bean by CXF configurer
            configure(factoryBean);

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

            return ((ClientProxy)Proxy.getInvocationHandler(factoryBean.create())).getClient();
        } else {
            
            ClientFactoryBean factoryBean = createClientFactoryBean();

            // configure client factory bean by CXF configurer
            configure(factoryBean);
            
            // setup client factory bean
            setupClientFactoryBean(factoryBean);
            
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
            
            checkName(factoryBean.getEndpointName(), "endpoint/port name");
            checkName(factoryBean.getServiceName(), "service name");
            return (Client)factoryBean.create();
        }
    }


    /**
     * Create a service factory bean
     */
    @Override
    ServerFactoryBean createServerFactoryBean() throws Exception  {
        
        // get service class
        Class<?> cls = getSEIClass();                
                
        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;

        if (cls == null) {
            if (!getDataFormat().equals(DataFormat.POJO)) {
                answer = new ServerFactoryBean(new WSDLServiceFactoryBean());
            } else {
                ObjectHelper.notNull(cls, CxfConstants.SERVICE_CLASS);
            }
        } else if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
            answer = new JaxWsServerFactoryBean();
        } else {
            answer = new ServerFactoryBean();
        }

        // configure server factory bean by CXF configurer
        configure(answer);
        
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

    void configure(Object beanInstance) {
        // check the ApplicationContext states first , and call the refresh if necessary
        if (((SpringCamelContext)getCamelContext()).getApplicationContext() instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext context = (ConfigurableApplicationContext)((SpringCamelContext)getCamelContext()).getApplicationContext();
            if (!context.isActive()) {
                context.refresh();
            }
        }
        configurer.configureBean(beanId, beanInstance);
    }
    
    // Properties
    // -------------------------------------------------------------------------
    public String getBeanId() {
        return beanId;
    }
    
    // this property will be set by spring
    public void setBeanId(String id) {       
        this.beanId = id;
    }
    
    public void setServiceNamespace(String serviceNamespace) {
        QName qn = getServiceName();
        if (qn == null) {
            setServiceName(new QName(serviceNamespace, "local"));
        } else {
            setServiceName(new QName(serviceNamespace, qn.getLocalPart()));
        }
    }

    public String getServiceNamespace() {
        QName qn = getServiceName();
        if (qn == null) {
            return null;
        }
        return qn.getNamespaceURI();
    }

    public void setServiceLocalName(String serviceLocalName) {
        QName qn = getServiceName();
        if (qn == null) {
            setServiceName(new QName("", serviceLocalName));
        } else {
            setServiceName(new QName(qn.getNamespaceURI(), serviceLocalName));
        }
    }

    public String getServiceLocalName() {
        QName qn = getServiceName();
        if (qn == null) {
            return null;
        }
        return qn.getLocalPart();
    }

    public String getEndpointLocalName() {
        QName qn = getPortName();
        if (qn == null) {
            return null;
        }
        return qn.getLocalPart();
    }

    public void setEndpointLocalName(String endpointLocalName) {
        QName qn = getPortName();
        if (qn == null) {
            setPortName(new QName("", endpointLocalName));
        } else {
            setPortName(new QName(qn.getNamespaceURI(), endpointLocalName));
        }
    }

    public void setEndpointNamespace(String endpointNamespace) {
        QName qn = getPortName();
        if (qn == null) {
            setPortName(new QName(endpointNamespace, "local"));
        } else {
            setPortName(new QName(endpointNamespace, qn.getLocalPart()));
        }
    }

    public String getEndpointNamespace() {
        QName qn = getPortName();
        if (qn == null) {
            return null;
        }
        return qn.getNamespaceURI();
    }
    

    @SuppressWarnings("deprecation")
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
        configurer = new ConfigurerImpl(applicationContext);

        if (bus == null) {
            if (Version.getCurrentVersion().startsWith("2.3")) {
                // Don't relate on the DefaultBus
                BusFactory factory = new SpringBusFactory(ctx);
                bus = factory.createBus();               
                BusWiringBeanFactoryPostProcessor.updateBusReferencesInContext(bus, ctx);
            } else {
                bus = BusWiringBeanFactoryPostProcessor.addDefaultBus(ctx);
            }
        }
    }
    
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
