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

package org.apache.camel.component.cxf.jaxrs;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.spring.CxfRsClientFactoryBeanDefinitionParser.SpringJAXRSClientFactoryBean;
import org.apache.camel.component.cxf.spring.CxfRsServerFactoryBeanDefinitionParser.SpringJAXRSServerFactoryBean;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;

/**
 * 
 */
public class CxfRsEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {
    private List<Class> resourceClasses;    
    private HeaderFilterStrategy headerFilterStrategy;
    private CxfRsBinding binding;
    
    public CxfRsEndpoint(String endpointUri, CamelContext camelContext) {
       super(endpointUri, camelContext);
    }
    
    public CxfRsEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }
    
    public HeaderFilterStrategy getHeaderFilterStrategy() {        
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }

    public Consumer createConsumer(Processor processor) throws Exception {        
        return new CxfRsConsumer(this, processor);
    }

    public Producer createProducer() throws Exception {        
        return new CxfRsProducer(this);
    }

    public boolean isSingleton() {        
        return false;
    }
    
    public void setBinding(CxfRsBinding binding) {
        this.binding = binding;
    }
    
    public CxfRsBinding getBinding() {
        if (binding == null) {
            binding = new DefaultCxfRsBinding();
        } 
        return binding;
    }
    
    protected void setupJAXRSServerFactoryBean(JAXRSServerFactoryBean sfb) {        
        // address
        sfb.setAddress(getEndpointUri());
        sfb.setResourceClasses(getResourceClasses());
        sfb.setStart(false);
    }
    
    protected void setupJAXRSClientFactoryBean(JAXRSClientFactoryBean cfb) {        
        // address
        cfb.setAddress(getEndpointUri());
        if (getResourceClasses() != null) {
            cfb.setResourceClass(getResourceClasses().get(0));
        }    
    }
   
    public JAXRSServerFactoryBean createJAXRSServerFactoryBean() {
        JAXRSServerFactoryBean answer = new SpringJAXRSServerFactoryBean();        
        setupJAXRSServerFactoryBean(answer);
        return answer;
    }
    
    public JAXRSClientFactoryBean createJAXRSClientFactoryBean() {
        JAXRSClientFactoryBean answer = new SpringJAXRSClientFactoryBean();
        setupJAXRSClientFactoryBean(answer);
        return answer;
    }
       
    public List<Class> getResourceClasses() {
        return resourceClasses;
    }

    public void setResourceClasses(List<Class> classes) {
        resourceClasses = classes;
    }

    public void setResourceClasses(Class... classes) {
        setResourceClasses(Arrays.asList(classes));
    }

}
