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

import com.example.customerservice.GetCustomersByName;

import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.spi.ClassResolver;
import org.junit.Test;

public class TypeNameStrategyTest {

    @Test
    public void testTypeNameStrategy() {
        TypeNameStrategy strategy = new TypeNameStrategy();
        ClassResolver resolver = new DefaultClassResolver();
        QName name = strategy.findQNameForSoapActionOrType("", GetCustomersByName.class, resolver);
        Assert.assertEquals("http://customerservice.example.com/", name.getNamespaceURI());
        Assert.assertEquals("getCustomersByName", name.getLocalPart());
    }
    
    @Test
    public void testNoAnnotation() {
        TypeNameStrategy strategy = new TypeNameStrategy();
        ClassResolver resolver = new DefaultClassResolver();
        try {
            strategy.findQNameForSoapActionOrType("", String.class, resolver);
            Assert.fail();
        } catch (RuntimeException e) {
            // Expected here
        }
    }
    
    @Test
    public void testNoPackageInfo() {
        TypeNameStrategy strategy = new TypeNameStrategy();
        ClassResolver resolver = new DefaultClassResolver();
        try {
            strategy.findQNameForSoapActionOrType("", AnnotatedClassWithoutNamespace.class, resolver);
            Assert.fail();
        } catch (RuntimeException e) {
            // Expected here as there is no package info and no namespace on class
        }
        QName name = strategy.findQNameForSoapActionOrType("", AnnotatedClassWithNamespace.class, resolver);
        Assert.assertEquals("test", name.getLocalPart());
        Assert.assertEquals("http://mynamespace", name.getNamespaceURI());
    }
}
