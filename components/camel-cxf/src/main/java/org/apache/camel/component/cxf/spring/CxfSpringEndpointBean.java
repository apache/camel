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
package org.apache.camel.component.cxf.spring;

import org.apache.camel.component.cxf.CxfEndpointBean;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.BusWiringBeanFactoryPostProcessor;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.version.Version;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class CxfSpringEndpointBean extends CxfEndpointBean implements ApplicationContextAware {
    private ApplicationContext applicationContext;
    
    public CxfSpringEndpointBean() {
        super();
    }
    
    public CxfSpringEndpointBean(ReflectionServiceFactoryBean factory) {
        super(factory);
    }
    
    @SuppressWarnings("deprecation")
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
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