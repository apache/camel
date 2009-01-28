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
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.component.cxf.util.CxfEndpointUtils;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.configuration.spring.ConfigurerImpl;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Endpoint</a>
 *
 * @version $Revision$
 */
public class CxfSpringEndpoint extends CxfEndpoint {

    private CxfEndpointBean bean;
    private String beanId;
    private ConfigurerImpl configurer;
    private String serviceNamespace;
    private String serviceLocalName;
    private String endpointLocalName;
    private String endpointNamespace;
    
    public CxfSpringEndpoint(CamelContext context, CxfEndpointBean bean) throws Exception {
        super(bean.getAddress(), context);
        init(bean);
    }
    
    public CxfSpringEndpoint(CxfComponent component, CxfEndpointBean bean) throws Exception {
        super(bean.getAddress(), component);
        init(bean);
    }
    

    private void init(CxfEndpointBean bean) throws Exception {
        this.bean = bean;
        
        // set properties from bean which can be overridden by endpoint URI
        setPropertiesBean();
        
        // create configurer
        configurer = new ConfigurerImpl(((SpringCamelContext)getCamelContext())
            .getApplicationContext());
    }

    /**
     * Read properties from the CxfEndpointBean and copy them to the 
     * properties of this class.  Note that the properties values can 
     * be overridden by values in URI query as the DefaultComponent 
     * will perform "setProperties" later (after the constructor). 
     */
    private void setPropertiesBean() throws Exception {
        if (bean.getProperties() != null) {
            Map<String, Object> copy = new HashMap<String, Object>();
            copy.putAll(bean.getProperties());
            
            // pass the copy the method modifies the properties map
            IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(), 
                    this, copy);      
        }
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
            answer = ClassLoaderUtils.loadClass(getServiceClass(), getClass());
        } else {
            answer = bean.getServiceClass();
        }
        return answer;
    }
    
    protected Bus doGetBus() {
        return bean.getBus();
    }
    
    public CxfEndpointBean getBean() {
        return bean;
    }
    
    public String getBeanId() {
        return beanId;
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
        ObjectHelper.notNull(cls, CxfConstants.SERVICE_CLASS);

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
    }


    /**
     * Create a service factory bean
     */
    @Override
    ServerFactoryBean createServerFactoryBean() throws Exception  {
        
        // get service class
        Class<?> cls = getSEIClass();        
        ObjectHelper.notNull(cls, CxfConstants.SERVICE_CLASS);
        
        // create server factory bean
        // create server factory bean
        // Shouldn't use CxfEndpointUtils.getServerFactoryBean(cls) as it is for
        // CxfSoapComponent
        ServerFactoryBean answer = null;
        if (CxfEndpointUtils.hasWebServiceAnnotation(cls)) {
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

        return answer;
    }

    void configure(Object beanInstance) {
        configurer.configureBean(beanId, beanInstance);
    }
    
    // Properties
    // -------------------------------------------------------------------------
    public void setBeanId(String id) {        
        this.beanId = id;
    }
    
    public void setServiceNamespace(String serviceNamespace) {
        this.serviceNamespace = serviceNamespace;
    }


    public String getServiceNamespace() {
        return serviceNamespace;
    }


    public void setServiceLocalName(String serviceLocalName) {
        this.serviceLocalName = serviceLocalName;
    }


    public String getServiceLocalName() {
        return serviceLocalName;
    }

    public String getEndpointLocalName() {
        return endpointLocalName;
    }

    public void setEndpointLocalName(String endpointLocalName) {
        this.endpointLocalName = endpointLocalName;
    }


    public void setEndpointNamespace(String endpointNamespace) {
        this.endpointNamespace = endpointNamespace;
    }


    public String getEndpointNamespace() {
        return endpointNamespace;
    }
    
    
}
