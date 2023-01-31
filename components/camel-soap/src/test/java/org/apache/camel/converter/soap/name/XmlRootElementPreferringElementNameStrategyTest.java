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

import java.io.Serializable;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import javax.xml.namespace.QName;

import org.apache.camel.converter.soap.name.testpackage.RequestWithDefaultNs;
import org.apache.camel.dataformat.soap.name.XmlRootElementPreferringElementNameStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class XmlRootElementPreferringElementNameStrategyTest {

    private static final String DEFAULT_NS = "##default";
    private static final String CUSTOM_NS = "http://test.com/sample";
    private static final String LOCAL_NAME = "sample";
    private XmlRootElementPreferringElementNameStrategy ens = new XmlRootElementPreferringElementNameStrategy();

    @Test
    public void testFindQNameForSoapActionOrTypeWithXmlSchemaPresent() throws Exception {
        QName qname = ens.findQNameForSoapActionOrType("abc", RequestWithDefaultNs.class);
        assertEquals("foo", qname.getLocalPart(), "local names must match");
        assertEquals("baz", qname.getNamespaceURI(), "namespace must match");
    }

    @Test
    public void testFindQNameForSoapActionOrType() throws Exception {
        QName qname = ens.findQNameForSoapActionOrType(DEFAULT_NS, Request.class);
        assertEquals(LOCAL_NAME, qname.getLocalPart(), "local names must match");
        assertEquals(CUSTOM_NS, qname.getNamespaceURI(), "namespace must match");

        qname = ens.findQNameForSoapActionOrType(CUSTOM_NS, Request.class);
        assertEquals(LOCAL_NAME, qname.getLocalPart(), "local names must match");
        assertEquals(CUSTOM_NS, qname.getNamespaceURI(), "namespace must match");
    }

    @Test
    public void testFindExceptionForFaultName() throws Exception {
        final QName faultName = new QName(LOCAL_NAME, CUSTOM_NS);

        assertThrows(UnsupportedOperationException.class,
                () -> ens.findExceptionForFaultName(faultName));
    }

    @XmlType(name = "", propOrder = { LOCAL_NAME })
    @XmlRootElement(name = LOCAL_NAME, namespace = CUSTOM_NS)
    public class Request implements Serializable {

        private static final long serialVersionUID = 1L;

    }

}
