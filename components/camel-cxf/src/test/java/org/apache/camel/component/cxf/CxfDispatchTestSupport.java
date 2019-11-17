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
package org.apache.camel.component.cxf;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.ws.Endpoint;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for testing arbitrary payload
 */
public abstract class CxfDispatchTestSupport extends CamelSpringTestSupport {
    protected static final String DISPATCH_NS = "http://camel.apache.org/cxf/jaxws/dispatch";
    protected static final String INVOKE_NAME = "Invoke";
    protected static final String INVOKE_ONEWAY_NAME = "InvokeOneWay";

    protected static final String PAYLOAD_TEMPLATE = "<ns1:greetMe xmlns:ns1=\"http://apache.org/hello_world_soap_http/types\"><ns1:requestType>%s</ns1:requestType></ns1:greetMe>";
    protected static final String PAYLOAD_ONEWAY_TEMPLATE = "<ns1:greetMeOneWay xmlns:ns1=\"http://apache.org/hello_world_soap_http/types\"><ns1:requestType>%s</ns1:requestType></ns1:greetMeOneWay>";
    protected static final String MESSAGE_TEMPLATE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" 
        + PAYLOAD_TEMPLATE
        + "</soap:Body></soap:Envelope>";
    protected static final String MESSAGE_ONEWAY_TEMPLATE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" 
        + PAYLOAD_ONEWAY_TEMPLATE
        + "</soap:Body></soap:Envelope>";
    private static DocumentBuilderFactory documentBuilderFactory;

    protected Endpoint endpoint;
    private int port = CXFTestSupport.getPort1();
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Before
    public void startService() {
        Object implementor = new GreeterImpl();
        String address = "http://localhost:" + port + "/"
            + getClass().getSimpleName() + "/SoapContext/GreeterPort";
        endpoint = Endpoint.publish(address, implementor); 
    }
    
    @After
    public void stopService() {
        if (endpoint != null) {
            endpoint.stop();
        }
    }
    
    protected static String getResponseType(Element node) {
        NodeList nodes = node.getElementsByTagNameNS("http://apache.org/hello_world_soap_http/types", "responseType");
        if (nodes != null && nodes.getLength() == 1) {
            Node c = nodes.item(0).getFirstChild();
            if (c != null) {
                return c.getNodeValue();
            }
        }
        return null;
    }
    
    protected static synchronized DocumentBuilderFactory getDocumentBuilderFactory() {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setIgnoringElementContentWhitespace(true);
            documentBuilderFactory.setIgnoringComments(true);
        }
        return documentBuilderFactory;
    }

}
