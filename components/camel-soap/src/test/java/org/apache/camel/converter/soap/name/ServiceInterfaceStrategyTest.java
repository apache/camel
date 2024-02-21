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
package org.apache.camel.converter.soap.name;

import javax.xml.namespace.QName;

import com.example.customerservice.CustomerService;
import com.example.customerservice.GetCustomersByName;
import com.example.customerservice.GetCustomersByNameResponse;
import com.example.customerservice.multipart.MultiPartCustomerService;
import com.example.duplicateerror.ExceptionA;
import com.example.duplicateerror.ExceptionB;
import com.example.duplicateerror.TestService;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class ServiceInterfaceStrategyTest {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceInterfaceStrategyTest.class);

    @Test
    public void testServiceInterfaceStrategyWithClient() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(CustomerService.class, true);
        QName elName = strategy.findQNameForSoapActionOrType("", GetCustomersByName.class);
        assertEquals("http://customerservice.example.com/", elName.getNamespaceURI());
        assertEquals("getCustomersByName", elName.getLocalPart());

        QName elName2 = strategy.findQNameForSoapActionOrType("getCustomersByName", GetCustomersByName.class);
        assertEquals("http://customerservice.example.com/", elName2.getNamespaceURI());
        assertEquals("getCustomersByName", elName2.getLocalPart());

        // Tests the case where the soap action is found but the in type is null
        QName elName3 = strategy.findQNameForSoapActionOrType("http://customerservice.example.com/getAllCustomers",
                null);
        assertNull(elName3);

        QName elName4 = strategy.findQNameForSoapActionOrType("http://customerservice.example.com/getAllAmericanCustomers",
                null);
        assertNull(elName4);

        try {
            elName = strategy.findQNameForSoapActionOrType("test", Class.class);
            fail();
        } catch (RuntimeCamelException e) {
            LOG.debug("Caught expected message: {}", e.getMessage());
        }
    }

    @Test
    public void testServiceInterfaceStrategyWithServer() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(CustomerService.class, false);

        // Tests the case where the action is not found but the type is
        QName elName = strategy.findQNameForSoapActionOrType("", GetCustomersByNameResponse.class);
        assertEquals("http://customerservice.example.com/", elName.getNamespaceURI());
        assertEquals("getCustomersByNameResponse", elName.getLocalPart());

        // Tests the case where the soap action is found
        QName elName2 = strategy.findQNameForSoapActionOrType("http://customerservice.example.com/getCustomersByName",
                GetCustomersByName.class);
        assertEquals("http://customerservice.example.com/", elName2.getNamespaceURI());
        assertEquals("getCustomersByNameResponse", elName2.getLocalPart());

        // this tests the case that the soap action as well as the type are not
        // found
        try {
            elName = strategy.findQNameForSoapActionOrType("test", Class.class);
            fail();
        } catch (RuntimeCamelException e) {
            LOG.debug("Caught expected message: {}", e.getMessage());
        }
    }

    @Test
    public void testServiceInterfaceStrategyWithRequestWrapperAndClient() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(
                com.example.customerservice2.CustomerService.class, true);
        QName elName = strategy.findQNameForSoapActionOrType("", com.example.customerservice2.GetCustomersByName.class);
        assertEquals("http://customerservice2.example.com/", elName.getNamespaceURI());
        assertEquals("getCustomersByName", elName.getLocalPart());

        try {
            elName = strategy.findQNameForSoapActionOrType("test", Class.class);
            fail();
        } catch (RuntimeCamelException e) {
            LOG.debug("Caught expected message: {}", e.getMessage());
        }
    }

    @Test
    public void testWithNonWebservice() {
        try {
            new ServiceInterfaceStrategy(Object.class, true);
            fail("Should throw an exception for a class that is no webservice");
        } catch (IllegalArgumentException e) {
            LOG.debug("Caught expected message: {}", e.getMessage());
        }
    }

    @Test
    public void testMultiPart() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(MultiPartCustomerService.class, true);
        QName custNameQName
                = strategy.findQNameForSoapActionOrType("http://multipart.customerservice.example.com/getCustomersByName",
                        com.example.customerservice.multipart.GetCustomersByName.class);
        QName custTypeQName
                = strategy.findQNameForSoapActionOrType("http://multipart.customerservice.example.com/getCustomersByName",
                        com.example.customerservice.multipart.Product.class);

        assertEquals("http://multipart.customerservice.example.com/", custNameQName.getNamespaceURI());
        assertEquals("getCustomersByName", custNameQName.getLocalPart());

        assertEquals("http://multipart.customerservice.example.com/", custTypeQName.getNamespaceURI());
        assertEquals("product", custTypeQName.getLocalPart());
    }

    @Test
    public void testQNameToException() {
        ServiceInterfaceStrategy strategy = new ServiceInterfaceStrategy(TestService.class, true);
        QName soapExceptionMultipleDefined = new QName("http://www.example.com/duplicateerror", "soapException");
        assertEquals(ExceptionA.class,
                strategy.findExceptionForSoapActionAndFaultName("throwErrorA", soapExceptionMultipleDefined));
        assertEquals(ExceptionB.class,
                strategy.findExceptionForSoapActionAndFaultName("throwErrorB", soapExceptionMultipleDefined));

        // This is implementation dependant (position in HashMap) one of ExceptionA or ExceptionB
        Class<? extends Exception> multiDefinedException = strategy.findExceptionForFaultName(soapExceptionMultipleDefined);

        if (multiDefinedException != ExceptionA.class && multiDefinedException != ExceptionB.class) {
            fail("Not one of ExceptionA or ExceptionB");
        }
    }
}
