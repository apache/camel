/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jmxconnect;


import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * @version $Revision$
 */
public class CamelJmxConnectorSupport {
    public static final String DEFAULT_DESTINATION_PREFIX = "org.apache.camel.jmxconnect.";
    public static final String MBEAN_SERVER_NAME = "*";
    public static final String MBEAN_GROUP_NAME = "*";

    public static String getEndpointUri(JMXServiceURL serviceURL, String expectedProtocol) throws IOException {
        String protocol = serviceURL.getProtocol();
        if (!expectedProtocol.equals(protocol)) {
            throw new MalformedURLException("Wrong protocol " + protocol + " expecting " + expectedProtocol);
        }
        String path = serviceURL.getURLPath();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

}