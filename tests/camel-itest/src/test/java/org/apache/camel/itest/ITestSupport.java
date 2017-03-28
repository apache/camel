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

package org.apache.camel.itest;

import org.apache.camel.test.AvailablePortFinder;

/**
 * For test cases that use unique contexts, they can share the 
 * ports which will make things a bit faster as ports aren't opened
 * and closed all the time. 
 */
public final class ITestSupport {

    static final int PORT1 = AvailablePortFinder.getNextAvailable();
    static final int PORT2 = AvailablePortFinder.getNextAvailable();
    static final int PORT3 = AvailablePortFinder.getNextAvailable(61616);
    static final int PORT4 = AvailablePortFinder.getNextAvailable(61616);

    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("ITestSupport.port1", Integer.toString(PORT1));
        System.setProperty("ITestSupport.port2", Integer.toString(PORT2));
        System.setProperty("ITestSupport.port3", Integer.toString(PORT3));
        System.setProperty("ITestSupport.port4", Integer.toString(PORT4));
    }

    private ITestSupport() {
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
}
