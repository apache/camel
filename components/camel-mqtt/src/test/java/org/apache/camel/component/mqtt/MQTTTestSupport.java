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

package org.apache.camel.component.mqtt;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.test.AvailablePortFinder;

/**
 * For test cases that use unique contexts, they can share the 
 * ports which will make things a bit faster as ports aren't opened
 * and closed all the time. 
 */
public final class MQTTTestSupport {

    static final int PORT1 = AvailablePortFinder.getNextAvailable();  
    static final String CONNECTION;
    private static final String HOST;

    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("MQTTTestSupport.port1", Integer.toString(PORT1));
        CONNECTION = "mqtt://127.0.0.1:" + PORT1;
        HOST = "tcp://127.0.0.1:" + PORT1;
    }
    
    private MQTTTestSupport() {
    }
    
    public static int getPort(String name) {
        int port = AvailablePortFinder.getNextAvailable();
        System.setProperty(name, Integer.toString(port));
        return port;
    }
    
    public static int getPort1() {
        return PORT1;
    }

    public static String getConnection() {
        return CONNECTION;
    }

    public static String getHostForMQTTEndpoint() {
        return HOST;
    }



    public static BrokerService newBrokerService() throws Exception {
        BrokerService service = new BrokerService();
        service.setPersistent(false);
        service.setAdvisorySupport(false);
        service.addConnector(getConnection());

        return service;
    }

    public static MQTTComponent newComponent() throws Exception {
        MQTTComponent component = new MQTTComponent();
        component.setHost(getHostForMQTTEndpoint());

        return component;
    }
}
