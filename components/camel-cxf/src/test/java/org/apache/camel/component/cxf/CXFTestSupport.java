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

import org.apache.camel.test.AvailablePortFinder;

/**
 * For test cases that use unique contexts, they can share the 
 * ports which will make things a bit faster as ports aren't opened
 * and closed all the time. 
 */
public final class CXFTestSupport {

    static final int PORT1 = AvailablePortFinder.getNextAvailable();  
    static final int PORT2 = AvailablePortFinder.getNextAvailable();  
    static final int PORT3 = AvailablePortFinder.getNextAvailable();  
    static final int PORT4 = AvailablePortFinder.getNextAvailable();  
    static final int PORT5 = AvailablePortFinder.getNextAvailable();  
    static final int PORT6 = AvailablePortFinder.getNextAvailable();
    static final int SSL_PORT = AvailablePortFinder.getNextAvailable();

    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("CXFTestSupport.port1", Integer.toString(PORT1));
        System.setProperty("CXFTestSupport.port2", Integer.toString(PORT2));
        System.setProperty("CXFTestSupport.port3", Integer.toString(PORT3));
        System.setProperty("CXFTestSupport.port4", Integer.toString(PORT4));
        System.setProperty("CXFTestSupport.port5", Integer.toString(PORT5));
        System.setProperty("CXFTestSupport.port6", Integer.toString(PORT6));
        System.setProperty("CXFTestSupport.sslPort", Integer.toString(SSL_PORT));
        System.setProperty("org.apache.cxf.transports.http_jetty.DontClosePort", "true");
    }
    
    private CXFTestSupport() {
    }
    
    public static int getPort(String name) {
        int port = AvailablePortFinder.getNextAvailable();
        System.setProperty(name, Integer.toString(port));
        return port;
    }
    
    public static int getPort1() {
        return PORT1;
    }

    public static int getPort2() {
        return PORT2;
    }

    public static int getPort3() {
        return PORT3;
    }

    public static int getPort4() {
        return PORT4;
    }

    public static int getPort5() {
        return PORT5;
    }

    public static int getPort6() {
        return PORT6;
    }

    public static int getSslPort() {
        return SSL_PORT;
    }
}
