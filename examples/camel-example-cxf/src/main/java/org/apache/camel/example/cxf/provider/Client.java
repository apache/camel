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

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

/**
 * A simple client that uses the JAX-WS Dispatch API to call 
 * a service endpoint exposed in a servlet container.
 */
public final class Client {

    // The endpoint address of the service
    String endpointAddress;

    public Client(String endpointAddress) {
        this.endpointAddress = endpointAddress;
    }

    public String invoke() throws Exception {
        // Service Qname as defined in the WSDL.
        QName serviceName = new QName("http://apache.org/hello_world_soap_http", "SOAPService");

        // Port QName as defined in the WSDL.
        QName portName = new QName("http://apache.org/hello_world_soap_http", "SoapOverHttpRouter");

        // Create a dynamic Service instance
        Service service = Service.create(serviceName);

        // Add a port to the Service
        service.addPort(portName, SOAPBinding.SOAP11HTTP_BINDING, endpointAddress);

        // Create a dispatch instance
        Dispatch<SOAPMessage> dispatch = service.createDispatch(portName, SOAPMessage.class,
                                                                Service.Mode.MESSAGE);

        // Use Dispatch as BindingProvider
        BindingProvider bp = dispatch;

        MessageFactory factory = ((SOAPBinding)bp.getBinding()).getMessageFactory();

        // Create SOAPMessage Request
        SOAPMessage request = factory.createMessage();

        // Request Body
        SOAPBody body = request.getSOAPBody();

        // Compose the soap:Body payload
        QName payloadName = new QName("http://apache.org/hello_world_soap_http/types", "greetMe", "ns1");

        SOAPBodyElement payload = body.addBodyElement(payloadName);

        SOAPElement message = payload.addChildElement("requestType");

        message.addTextNode("Hello Camel!!");
        
        System.out.println("Send out the request: Hello Camel!!");

        // Invoke the endpoint synchronously
        // Invoke endpoint operation and read response
        SOAPMessage reply = dispatch.invoke(request);
        
        // process the reply
        body = reply.getSOAPBody();
        
        QName responseName = new QName("http://apache.org/hello_world_soap_http/types", "greetMeResponse");

        SOAPElement bodyElement = (SOAPElement)body.getChildElements(responseName).next();

        String responseMessageText = bodyElement.getTextContent();

        System.out.println("Get the response: " + responseMessageText);
        
        return responseMessageText;
    }

    public static void main(String[] args) throws Exception {
        Client client = new Client("http://localhost:9000/GreeterContext/SOAPMessageService");
        client.invoke();
    }

}
