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

import com.example.customerservice.GetCustomersByName;
import org.apache.camel.dataformat.soap.name.TypeNameStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TypeNameStrategyTest {

    @Test
    public void testTypeNameStrategy() {
        TypeNameStrategy strategy = new TypeNameStrategy();
        QName name = strategy.findQNameForSoapActionOrType("", GetCustomersByName.class);
        assertEquals("http://customerservice.example.com/", name.getNamespaceURI());
        assertEquals("getCustomersByName", name.getLocalPart());
    }

    @Test
    public void testNoAnnotation() {
        TypeNameStrategy strategy = new TypeNameStrategy();
        try {
            strategy.findQNameForSoapActionOrType("", String.class);
            fail();
        } catch (RuntimeException e) {
            // Expected here
        }
    }

    @Test
    public void testNoPackageInfo() {
        TypeNameStrategy strategy = new TypeNameStrategy();
        QName name = strategy.findQNameForSoapActionOrType("", AnnotatedClassWithoutNamespace.class);
        assertEquals("test", name.getLocalPart());
        assertEquals("##default", name.getNamespaceURI());

        QName name2 = strategy.findQNameForSoapActionOrType("", AnnotatedClassWithNamespace.class);
        assertEquals("test", name2.getLocalPart());
        assertEquals("http://mynamespace", name2.getNamespaceURI());
    }
}
