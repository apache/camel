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

import java.io.InputStream;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPMessage;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.apache.cxf.staxutils.StaxUtils;

public class SoapTargetBean {
    private static QName sayHi = new QName("http://apache.org/hello_world_soap_http/types", "sayHi");
    private static QName greetMe = new QName("http://apache.org/hello_world_soap_http/types", "greetMe");
    private SOAPMessage sayHiResponse;
    private SOAPMessage greetMeResponse;

    public SoapTargetBean() {

        try {
            MessageFactory factory = MessageFactory.newInstance();
            InputStream is = getClass().getResourceAsStream("sayHiDocLiteralResp.xml");
            sayHiResponse =  factory.createMessage(null, is);
            is.close();
            is = getClass().getResourceAsStream("GreetMeDocLiteralResp.xml");
            greetMeResponse =  factory.createMessage(null, is);
            is.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public SOAPMessage invokeString(String request) {
        SOAPMessage response = null;
        if (request.indexOf("sayHi") > 0) {
            response = sayHiResponse;
        } else {
            response = greetMeResponse;
        }
        return response;
    }

    public SOAPMessage invokeSoapMessage(SOAPMessage request) {
        SOAPMessage response = null;
        try {
            SOAPBody body = request.getSOAPBody();
            Node n = body.getFirstChild();

            while (n.getNodeType() != Node.ELEMENT_NODE) {
                n = n.getNextSibling();
            }
            if (n.getLocalName().equals(sayHi.getLocalPart())) {
                response = sayHiResponse;
            } else if (n.getLocalName().equals(greetMe.getLocalPart())) {
                response = greetMeResponse;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    //Simulates a stream based processor or producer (e.g., file EP)
    public SOAPMessage invokeStream(InputStream in) {
        SOAPMessage response = null;
        try {
            Document doc = StaxUtils.read(in);
            if (doc.getElementsByTagNameNS(greetMe.getNamespaceURI(), 
                                           sayHi.getLocalPart()).getLength() == 1) {
                response = sayHiResponse;
            } else if (doc.getElementsByTagNameNS(greetMe.getNamespaceURI(), 
                                                  greetMe.getLocalPart()).getLength() == 1) {
                response = greetMeResponse;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }
}
