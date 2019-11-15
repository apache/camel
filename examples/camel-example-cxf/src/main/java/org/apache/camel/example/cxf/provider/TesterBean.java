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
package org.apache.camel.example.cxf.provider;

import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import org.apache.camel.Exchange;

/**
 * A simple bean demonstrating the processing of a SOAPMessage routed by Camel
 */
//START SNIPPET: e1
public class TesterBean {

    public SOAPMessage processSOAP(Exchange exchange) {
        // Since the Camel-CXF endpoint uses a list to store the parameters
        // and bean component uses the bodyAs expression to get the value
        // we'll need to deal with the parameters ourself
        SOAPMessage soapMessage = (SOAPMessage)exchange.getIn().getBody(List.class).get(0);
        if (soapMessage == null) {
            System.out.println("Incoming null message detected...");
            return createDefaultSoapMessage("Greetings from Apache Camel!!!!", "null");
        }

        try {
            SOAPPart sp = soapMessage.getSOAPPart();
            SOAPEnvelope se = sp.getEnvelope();
            SOAPBody sb = se.getBody();
            String requestText = sb.getFirstChild().getTextContent();
            System.out.println(requestText);
            return createDefaultSoapMessage("Greetings from Apache Camel!!!!", requestText);
        } catch (Exception e) {
            e.printStackTrace();
            return createDefaultSoapMessage("Greetings from Apache Camel!!!!", e.getClass().getName());
        }
    }

    public static SOAPMessage createDefaultSoapMessage(String responseMessage, String requestMessage) {
        try {
            SOAPMessage soapMessage = MessageFactory.newInstance().createMessage();
            SOAPBody body = soapMessage.getSOAPPart().getEnvelope().getBody();

            QName payloadName = new QName("http://apache.org/hello_world_soap_http/types", "greetMeResponse", "ns1");

            SOAPBodyElement payload = body.addBodyElement(payloadName);

            SOAPElement message = payload.addChildElement("responseType");

            message.addTextNode(responseMessage + " Request was  " + requestMessage);
            return soapMessage;
        } catch (SOAPException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
//END SNIPPET: e1
