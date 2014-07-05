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

import java.io.Serializable;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.apache.camel.converter.soap.name.testpackage.RequestWithDefaultNs;
import org.apache.camel.dataformat.soap.name.XmlRootElementPreferringElementNameStrategy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XmlRootElementPreferringElementNameStrategyTest {

    private static final String DEFAULT_NS = "##default";
    private static final String CUSTOM_NS = "http://test.com/sample";
    private static final String LOCAL_NAME = "sample";
    private XmlRootElementPreferringElementNameStrategy ens = new XmlRootElementPreferringElementNameStrategy();

    @Test
    public void testFindQNameForSoapActionOrTypeWithXmlSchemaPresent() throws Exception {
        QName qname = ens.findQNameForSoapActionOrType("abc", RequestWithDefaultNs.class);
        assertEquals("local names must match", "foo", qname.getLocalPart());
        assertEquals("namespace must match", "baz", qname.getNamespaceURI());
    }

    @Test
    public void testFindQNameForSoapActionOrType() throws Exception {
        QName qname = ens.findQNameForSoapActionOrType(DEFAULT_NS, Request.class);
        assertEquals("local names must match", LOCAL_NAME, qname.getLocalPart());
        assertEquals("namespace must match", CUSTOM_NS, qname.getNamespaceURI());

        qname = ens.findQNameForSoapActionOrType(CUSTOM_NS, Request.class);
        assertEquals("local names must match", LOCAL_NAME, qname.getLocalPart());
        assertEquals("namespace must match", CUSTOM_NS, qname.getNamespaceURI());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFindExceptionForFaultName() throws Exception {
        ens.findExceptionForFaultName(new QName(LOCAL_NAME, CUSTOM_NS));
    }

    @XmlType(name = "", propOrder = {LOCAL_NAME})
    @XmlRootElement(name = LOCAL_NAME, namespace = CUSTOM_NS)
    public class Request implements Serializable {

        private static final long serialVersionUID = 1L;

    }

}
