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
package org.apache.camel.component.openshift;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;

public class OpenShiftComponent extends UriEndpointComponent {

    @Metadata(label = "security", secret = true)
    private String username;
    @Metadata(label = "security", secret = true)
    private String password;
    private String domain;
    private String server;

    public OpenShiftComponent() {
        super(OpenShiftEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String clientId = remaining;

        OpenShiftEndpoint endpoint = new OpenShiftEndpoint(uri, this);
        endpoint.setClientId(clientId);
        endpoint.setUsername(getUsername());
        endpoint.setPassword(getPassword());
        endpoint.setDomain(getDomain());
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The username to login to openshift server.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password for login to openshift server.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getDomain() {
        return domain;
    }

    /**
     * Domain name. If not specified then the default domain is used.
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getServer() {
        return server;
    }

    /**
     * Url to the openshift server.
     * If not specified then the default value from the local openshift configuration file ~/.openshift/express.conf is used.
     * And if that fails as well then "openshift.redhat.com" is used.
     */
    public void setServer(String server) {
        this.server = server;
    }
}
