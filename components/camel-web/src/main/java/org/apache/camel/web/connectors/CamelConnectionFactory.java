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
package org.apache.camel.web.connectors;

import java.io.IOException;

import org.apache.camel.web.connectors.jmx.CamelJmxConnection;

/**
 *
 */
public class CamelConnectionFactory {

    private static final String JMX_SERVICE_URL_PREFIX = "service:jmx:rmi:///jndi/rmi://";

    private String jmxHost = "localhost";

    private String jmxPort = "1099";

    private String jmxPath = "jmxrmi/camel";

    private static CamelConnectionFactory instance = new CamelConnectionFactory();

    private CamelConnectionFactory() {}

    public static CamelConnectionFactory getInstance() {
        return instance;
    }

    public CamelConnection getJmxConnection() throws IOException {
        String jmxServiceUrl = JMX_SERVICE_URL_PREFIX + jmxHost + ":" + jmxPort + "/" + jmxPath;
        return new CamelJmxConnection(jmxServiceUrl);
    }

    public CamelConnection getJmxConnection(String jmxHost, String jmxPort, String jmxPath) throws IOException {
        String jmxServiceUrl = JMX_SERVICE_URL_PREFIX + jmxHost + ":" + jmxPort + "/" + jmxPath;
        return new CamelJmxConnection(jmxServiceUrl);
    }

}