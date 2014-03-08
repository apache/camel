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

import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.service.factory.AbstractServiceConfiguration;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.service.invoker.Invoker;

/**
 * A service factory bean class that create a service factory without requiring a service class
 * (SEI).
 * It will pick the first one service name and first one port/endpoint name in the WSDL, if 
 * there is service name or port/endpoint name setted.
 * @version 
 */
public class WSDLServiceFactoryBean extends ReflectionServiceFactoryBean {
    
    public WSDLServiceFactoryBean() {
        // set up the service configure to help us find the service name and endpoint name from WSDL
        WSDLServiceConfiguration configuration = new WSDLServiceConfiguration(this);
        List<AbstractServiceConfiguration> list = new ArrayList<AbstractServiceConfiguration>();
        list.add(configuration);
        this.setServiceConfigurations(list);
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
