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

import javax.xml.ws.Endpoint;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;

public class Server {
    Endpoint endpointA;
    Endpoint endpointB;

    public void prepare() throws Exception {
        // Set a system property used to configure the server.  The examples all run on port 9091; 
        // however, the unit tests must run on a dynamic port.  As such, we make the port configurable
        // in the Spring context.
        System.setProperty("port", "9001");
        // setup the Camel context for the Camel transport
        // START SNIPPET: e1
        SpringBusFactory bf = new SpringBusFactory();
        BusFactory.setDefaultBus(null);
        Bus bus = bf.createBus("/org/apache/camel/example/camel/transport/CamelDestination.xml");
        BusFactory.setDefaultBus(bus);
        // END SNIPPET: e1
    }

    public void start() throws Exception {
        // start the endpoints
        System.out.println("Starting Server");
        // START SNIPPET: e2
        GreeterImpl implementor = new GreeterImpl();
        implementor.setSuffix("EndpointA");
        String address = "camel://direct:EndpointA";
        endpointA = Endpoint.publish(address, implementor);

        implementor = new GreeterImpl();
        implementor.setSuffix("EndpointB");
        address = "camel://direct:EndpointB";
        endpointB = Endpoint.publish(address, implementor);
        // END SNIPPET: e2
    }

    public void stop() {
        if (endpointA != null) {
            endpointA.stop();
        }
        if (endpointB != null) {
            endpointB.stop();
        }
    }
    

    public static void main(String args[]) throws Exception {
        Server server = new Server();
        server.prepare();
        server.start();
        
        System.out.println("Server ready...");

        Thread.sleep(5 * 60 * 1000);
        System.out.println("Server exiting");
        server.stop();
        System.exit(0);
    }
}
