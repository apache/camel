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

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Server {
    AbstractApplicationContext applicationContext;

    public void start() throws Exception {
        // Set a system property used to configure the server.  The example runs on port 9000; 
        // however, the unit tests must run on a dynamic port.  As such, we make the port configurable
        // in the Spring context.
        System.setProperty("port", "9000");
        // Setup the Camel context using Spring
        applicationContext = new ClassPathXmlApplicationContext("/META-INF/spring/CamelCXFProviderRouteConfig.xml");
    }
    
    public void stop() {
        if (applicationContext != null) {
            applicationContext.stop();
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
