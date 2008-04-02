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
package org.apache.camel.example.cxf.provider;

import javax.xml.ws.Endpoint;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;

public class Server {
    Endpoint endpoint;

    public void start() throws Exception {
        // Setup the Camel context using Spring
        SpringBusFactory bf = new SpringBusFactory();
        BusFactory.setDefaultBus(null);
        Bus bus = bf.createBus("/org/apache/camel/example/cxf/provider/CamelCXFRouteConfig.xml");
        BusFactory.setDefaultBus(bus);          
    }
    
    public void stop() {
        if (endpoint != null) {
            endpoint.stop();
        }    
    }
    

    public static void main(String args[]) throws Exception {
        Server server = new Server();
        System.out.println("Server ready...");

        Thread.sleep(5 * 60 * 1000);
        System.out.println("Server exiting");
        server.stop();
        System.exit(0);
    }
}
