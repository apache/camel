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
package org.apache.camel.component.cxf.common;

import org.apache.camel.test.AvailablePortFinder;

/**
 * Provides pre-allocated port numbers as system properties for test cases and Spring XML contexts.
 */
public final class CXFTestSupport {

    static final AvailablePortFinder.Port PORT1 = AvailablePortFinder.find();
    static final AvailablePortFinder.Port PORT2 = AvailablePortFinder.find();
    static final AvailablePortFinder.Port PORT3 = AvailablePortFinder.find();
    static final AvailablePortFinder.Port PORT4 = AvailablePortFinder.find();
    static final AvailablePortFinder.Port PORT5 = AvailablePortFinder.find();
    static final AvailablePortFinder.Port PORT6 = AvailablePortFinder.find();
    static final AvailablePortFinder.Port PORT7 = AvailablePortFinder.find();
    static final AvailablePortFinder.Port SSL_PORT = AvailablePortFinder.find();

    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("CXFTestSupport.port1", PORT1.toString());
        System.setProperty("CXFTestSupport.port2", PORT2.toString());
        System.setProperty("CXFTestSupport.port3", PORT3.toString());
        System.setProperty("CXFTestSupport.port4", PORT4.toString());
        System.setProperty("CXFTestSupport.port5", PORT5.toString());
        System.setProperty("CXFTestSupport.port6", PORT6.toString());
        System.setProperty("CXFTestSupport.port7", PORT7.toString());
        System.setProperty("CXFTestSupport.sslPort", SSL_PORT.toString());
    }

    private CXFTestSupport() {
    }

    public static int getPort(String name) {
        int port = AvailablePortFinder.find().getPort();
        System.setProperty(name, Integer.toString(port));
        return port;
    }

    public static int getPort1() {
        return PORT1.getPort();
    }

    public static int getPort2() {
        return PORT2.getPort();
    }

    public static int getPort3() {
        return PORT3.getPort();
    }

    public static int getPort4() {
        return PORT4.getPort();
    }

    public static int getPort5() {
        return PORT5.getPort();
    }

    public static int getPort6() {
        return PORT6.getPort();
    }

    public static int getPort7() {
        return PORT7.getPort();
    }

    public static int getSslPort() {
        return SSL_PORT.getPort();
    }
}
