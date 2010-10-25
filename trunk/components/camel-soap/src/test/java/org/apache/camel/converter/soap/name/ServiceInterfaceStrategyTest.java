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
package org.apache.camel.converter.soap.name;

import javax.xml.namespace.QName;

import junit.framework.Assert;

import com.example.customerservice.CustomerService;
import com.example.customerservice.GetCustomersByName;
import com.example.customerservice.GetCustomersByNameResponse;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dataformat.soap.name.ElementNameStrategy;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class ServiceInterfaceStrategyTest {
    private static final Log LOG = LogFactory.getLog(ServiceInterfaceStrategyTest.class);
    
    @Test
    public void testServiceInterfaceStrategyWithClient() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(CustomerService.class, true);
        QName elName = strategy.findQNameForSoapActionOrType("", GetCustomersByName.class);
        Assert.assertEquals("http://customerservice.example.com/", elName.getNamespaceURI());
        Assert.assertEquals("getCustomersByName", elName.getLocalPart());
        
        QName elName2 = strategy.findQNameForSoapActionOrType("getCustomersByName", GetCustomersByName.class);
        Assert.assertEquals("http://customerservice.example.com/", elName2.getNamespaceURI());
        Assert.assertEquals("getCustomersByName", elName2.getLocalPart());
        
        try {
            elName = strategy.findQNameForSoapActionOrType("test", Class.class);
            Assert.fail();
        } catch (RuntimeCamelException e) {
            LOG.debug("Caught expected message: " + e.getMessage());
        }
    }
    
    @Test
    public void testServiceInterfaceStrategyWithServer() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(CustomerService.class, false);
        QName elName = strategy.findQNameForSoapActionOrType("", GetCustomersByNameResponse.class);
        Assert.assertEquals("http://customerservice.example.com/", elName.getNamespaceURI());
        Assert.assertEquals("getCustomersByNameResponse", elName.getLocalPart());

        QName elName2 = strategy.findQNameForSoapActionOrType("getCustomersByName", GetCustomersByName.class);
        Assert.assertEquals("http://customerservice.example.com/", elName2.getNamespaceURI());
        Assert.assertEquals("getCustomersByNameResponse", elName2.getLocalPart());
        
        try {
            elName = strategy.findQNameForSoapActionOrType("test", Class.class);
            Assert.fail();
        } catch (RuntimeCamelException e) {
            LOG.debug("Caught expected message: " + e.getMessage());
        }
    }
    
    @Test
    public void testServiceInterfaceStrategyWithRequestWrapperAndClient() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(com.example.customerservice2.CustomerService.class, true);
        QName elName = strategy.findQNameForSoapActionOrType("", com.example.customerservice2.GetCustomersByName.class);
        Assert.assertEquals("http://customerservice2.example.com/", elName.getNamespaceURI());
        Assert.assertEquals("getCustomersByName", elName.getLocalPart());
        
        try {
            elName = strategy.findQNameForSoapActionOrType("test", Class.class);
            Assert.fail();
        } catch (RuntimeCamelException e) {
            LOG.debug("Caught expected message: " + e.getMessage());
        }
    }
    
    @Test
    public void testWithNonWebservice() {
        try {
            new ServiceInterfaceStrategy(ElementNameStrategy.class, true);
            Assert.fail();
        } catch (RuntimeCamelException e) {
            LOG.debug("Caught expected message: " + e.getMessage());
        }
    }
}
