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
package org.apache.camel.component.smb;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

@Component("smb")
public class SmbComponent extends DefaultComponent {

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Host IP or address must be configured on endpoint using syntax smb:host:port");
        }

        final SmbEndpoint endpoint = new SmbEndpoint(uri, this);

        final String hostPart = StringHelper.before(remaining, "/");
        if (hostPart != null && hostPart.contains(":")) {
            parseHost(hostPart, endpoint);
        } else {
            endpoint.setHostname(hostPart);
        }

        String path = StringHelper.after(remaining, "/");

        setProperties(endpoint, parameters);
        endpoint.setShareName(path);

        return endpoint;
    }

    private static void parseHost(String hostPart, SmbEndpoint endpoint) {
        // Host part is in the format hostname:port or ip:port
        String host = StringHelper.before(hostPart, ":");
        if (ObjectHelper.isEmpty(host)) {
            throw new IllegalArgumentException("Invalid host or address: " + hostPart);
        }

        endpoint.setHostname(host);

        String port = StringHelper.after(hostPart, ":");
        if (ObjectHelper.isEmpty(port)) {
            throw new IllegalArgumentException("Invalid port given on host: " + hostPart);
        }

        endpoint.setPort(Integer.parseInt(port));
    }
}
