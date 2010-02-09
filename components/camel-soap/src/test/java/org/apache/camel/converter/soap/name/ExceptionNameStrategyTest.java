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

import com.example.customerservice.NoSuchCustomerException;

import org.apache.camel.dataformat.soap.name.ExceptionNameStrategy;
import org.junit.Test;

public class ExceptionNameStrategyTest {

    @Test
    public void testServiceInterfaceStrategyWithServer() {
        ExceptionNameStrategy strategy = new ExceptionNameStrategy();
        QName elName = strategy.findQNameForSoapActionOrType("", NoSuchCustomerException.class);
        Assert.assertEquals("http://customerservice.example.com/", elName.getNamespaceURI());
        Assert.assertEquals("NoSuchCustomer", elName.getLocalPart());

        try {
            elName = strategy.findQNameForSoapActionOrType("", Class.class);
            Assert.fail();
        } catch (Exception e) {
            // expected here
        }
    }
}
