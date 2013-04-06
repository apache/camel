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
package org.apache.camel.example.camel.transport;

import java.net.MalformedURLException;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.PingMeFault;
import org.apache.hello_world_soap_http.types.FaultDetail;

public final class Client {

    private static final QName SERVICE_NAME
        = new QName("http://apache.org/hello_world_soap_http", "CamelService");
    private static final QName PORT_NAME
        = new QName("http://apache.org/hello_world_soap_http", "CamelPort");
    private Service service;
    private Greeter port;


    public Client(String address) throws MalformedURLException {
        // create the client from the SEI
        service = Service.create(SERVICE_NAME);
        service.addPort(PORT_NAME, javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING,
                        address);
        
        System.out.println("Acquiring router port ...");
        port = service.getPort(PORT_NAME, Greeter.class);

    }

    public Greeter getProxy() {
        return port;
    }
    
    public static void main(String args[]) throws Exception {
        // set the client's service access point
        Client client = new Client("http://localhost:9001/GreeterContext/GreeterPort");
        // invoking the services
        client.invoke();
    }

    public void invoke() throws Exception {

        String resp;

        System.out.println("Invoking sayHi...");
        resp = port.sayHi();
        System.out.println("Server responded with: " + resp);
        System.out.println();

        System.out.println("Invoking greetMe... with Mike");
        resp = port.greetMe("Mike");
        System.out.println("Server responded with: " + resp);
        System.out.println();

        System.out.println("Invoking greetMe... with James");
        resp = port.greetMe("James");
        System.out.println("Server responded with: " + resp);
        System.out.println();

        System.out.println("Invoking greetMeOneWay...");
        port.greetMeOneWay(System.getProperty("user.name"));
        System.out.println("No response from server as method is OneWay");
        System.out.println();

        try {
            System.out.println("Invoking pingMe, expecting exception...");
            port.pingMe("hello");
        } catch (PingMeFault ex) {
            System.out.println("Expected exception: PingMeFault has occurred: " + ex.getMessage());
            FaultDetail detail = ex.getFaultInfo();
            System.out.println("FaultDetail major:" + detail.getMajor());
            System.out.println("FaultDetail minor:" + detail.getMinor());
        }

    }

}
