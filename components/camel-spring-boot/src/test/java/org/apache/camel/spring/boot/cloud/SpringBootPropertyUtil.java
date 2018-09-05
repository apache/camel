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
package org.apache.camel.spring.boot.cloud;

import org.apache.camel.test.AvailablePortFinder;

public final class SpringBootPropertyUtil {

    public static final int PORT1 = AvailablePortFinder.getNextAvailable();
    public static final int PORT2 = AvailablePortFinder.getNextAvailable();
    public static final int PORT3 = AvailablePortFinder.getNextAvailable();
    public static final String HOSTNAME = "localhost:";
    
    private SpringBootPropertyUtil() {

    }

    
    public static  String getDiscoveryServices() {    

        StringBuffer dservice = new StringBuffer();
        dservice.append(HOSTNAME);
        dservice.append(PORT1);
        dservice.append(",");
        dservice.append(HOSTNAME);
        dservice.append(PORT2);
        dservice.append(",");
        dservice.append(HOSTNAME);
        dservice.append(PORT3);
        return dservice.toString();

    }

    public static String getServiceFilterBlacklist() {

        StringBuffer filterBlacklistString = new StringBuffer();
        filterBlacklistString.append(HOSTNAME);
        filterBlacklistString.append(PORT2);
        return filterBlacklistString.toString();

    }

}
