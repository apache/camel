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
package org.apache.camel.example.cxf.httptojms;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfComponent;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.hello_world_soap_http.Greeter;

/**
 * An example for demonstrating how Camel works as a Router.
 * This example shows how Camel can route a SOAP client's HTTP request
 * to a SOAP over JMS Service.
 */
public final class CamelCxfExample {
    private static final String ROUTER_ADDRESS = "http://localhost:{{routerPort}}/SoapContext/SoapPort";
    private static final String SERVICE_ADDRESS = "jms:jndi:dynamicQueues/test.soap.jmstransport.queue?jndiInitialContextFactory="
            + "org.apache.activemq.jndi.ActiveMQInitialContextFactory&jndiConnectionFactoryName="
            + "ConnectionFactory&jndiURL=vm://localhost";
    private static final String SERVICE_CLASS = "serviceClass=org.apache.hello_world_soap_http.Greeter";
    private static final String WSDL_LOCATION = "wsdlURL=wsdl/hello_world.wsdl";
    private static final String SERVICE_NAME = "serviceName={http://apache.org/hello_world_soap_http}SOAPService";
    private static final String SOAP_OVER_HTTP_ROUTER = "portName={http://apache.org/hello_world_soap_http}SoapOverHttpRouter";
    
    private static final String ROUTER_ENDPOINT_URI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS + "&"
                                                   + WSDL_LOCATION + "&" + SERVICE_NAME + "&" + SOAP_OVER_HTTP_ROUTER + "&dataFormat=POJO";
   
    private CamelCxfExample() {
    }
    
    public static class MyRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            // Set system properties for use with Camel property placeholders for running the example tests.
            System.setProperty("routerPort", String.valueOf(AvailablePortFinder.getNextAvailable()));
            System.setProperty("servicePort", String.valueOf(AvailablePortFinder.getNextAvailable()));
            CxfComponent cxfComponent = new CxfComponent(getContext());
            CxfEndpoint serviceEndpoint = new CxfEndpoint(SERVICE_ADDRESS, cxfComponent);
            serviceEndpoint.setServiceClass(Greeter.class); 
            
            // Here we just pass the exception back, don't need to use errorHandler
            errorHandler(noErrorHandler());
            from(ROUTER_ENDPOINT_URI).to(serviceEndpoint);
        }
        
    }

    public static void main(String args[]) throws Exception {
        
        // Set system properties for use with Camel property placeholders for running the examples.
        System.setProperty("routerPort", "9001");
        System.setProperty("servicePort", "9003");

        // START SNIPPET: e1
        CamelContext context = new DefaultCamelContext();
        // END SNIPPET: e1
        // Set up the JMS broker and the CXF SOAP over JMS server
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
                    CxfComponent cxfComponent = new CxfComponent(getContext());
                    CxfEndpoint serviceEndpoint = new CxfEndpoint(SERVICE_ADDRESS, cxfComponent);
                    serviceEndpoint.setServiceClass(Greeter.class);
                    // Here we just pass the exception back, don't need to use errorHandler
                    errorHandler(noErrorHandler());
                    from(ROUTER_ENDPOINT_URI).to(serviceEndpoint);
                }
            });
            // END SNIPPET: e3
            String address = ROUTER_ADDRESS.replace("{{routerPort}}", System.getProperty("routerPort"));
            // Starting the routing context
            // Using the CXF Client to kick off the invocations
            // START SNIPPET: e4
            context.start();
            Client client = new Client(address + "?wsdl");
            // END SNIPPET: e4
            // Now everything is set up - let's start the context

            client.invoke();
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
