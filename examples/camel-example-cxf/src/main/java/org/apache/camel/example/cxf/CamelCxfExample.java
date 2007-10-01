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
package org.apache.camel.example.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.jms.JmsBroker;

import org.apache.camel.impl.DefaultCamelContext;

/**
 * An example class for demonstrating camel works as Router.
 * This example shows how camel can route a SOAP over HTTP client's request
 * to a SOAP over JMS Service. 
 *
 */
public final class CamelCxfExample {
    private static final String ROUTER_ADDRESS = "http://localhost:9001/SoapContext/SoapPort";
    private static final String SERVICE_ADDRESS = "http://localhost:9000/SoapContext/SoapPort";
    private static final String SERVICE_CLASS = "serviceClass=org.apache.hello_world_soap_http.Greeter";
    private static final String WSDL_LOCATION = "wsdlURL=wsdl/hello_world.wsdl";
    private static final String SERVICE_NAME = "serviceName=%7bhttp://apache.org/hello_world_soap_http%7dSOAPService";
    private static final String SOAP_OVER_HTTP_ROUTER = "portName=%7bhttp://apache.org/hello_world_soap_http%7dSoapOverHttpRouter";
    private static final String SOAP_OVER_JMS = "portName=%7bhttp://apache.org/hello_world_soap_http%7dSoapOverJms";
        
    private static String ROUTER_ENDPOINT_URI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS + "&" 
                                                   + WSDL_LOCATION + "&" + SERVICE_NAME + "&" + SOAP_OVER_HTTP_ROUTER + "&dataFormat=POJO";
    private static String SERVICE_ENDPOINT_URI = "cxf://" + SERVICE_ADDRESS + "?" + SERVICE_CLASS + "&" 
                                                   + WSDL_LOCATION + "&" + SERVICE_NAME + "&" + SOAP_OVER_JMS + "&dataFormat=POJO";
    
    private CamelCxfExample() {        
    }
        
    public static void main(String args[]) throws Exception {
        
        // START SNIPPET: e1
        CamelContext context = new DefaultCamelContext();
        // END SNIPPET: e1
        // set up the jms broker and the CXF SOAP over JMS server
        // START SNIPPET: e2
        JmsBroker broker = new JmsBroker();
        Server server = new Server();
        try {     
            broker.start();
            server.start();
            // END SNIPPET: e2
            // Add some configuration by hand ...
            // START SNIPPET: e3
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    from(ROUTER_ENDPOINT_URI).to(SERVICE_ENDPOINT_URI);               
                }
            });
            // END SNIPPET: e3
            // Staring the routing context
            // Using the CXF Client to kick off the invocations 
            // START SNIPPET: e4
            context.start();
            Client client = new Client(ROUTER_ADDRESS + "?wsdl");
            // END SNIPPET: e4
            // Now everything is set up - lets start the context
             
            client.invock();
            Thread.sleep(1000);
            context.stop();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            server.stop();
            broker.stop();
            System.exit(0);
        }
        
    }
}
