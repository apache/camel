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
package org.apache.camel.component.nagios;

import java.net.URI;
import java.util.Map;

import com.googlecode.jsendnsca.core.NagiosSettings;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class NagiosComponent extends UriEndpointComponent {

    private NagiosConfiguration configuration;

    public NagiosComponent() {
        super(NagiosEndpoint.class);
        configuration = new NagiosConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI url = new URI(uri);

        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");
        NagiosConfiguration config = configuration.copy();
        config.configure(url);
        setProperties(config, parameters);

        NagiosEndpoint endpoint = new NagiosEndpoint(uri, this);
        endpoint.setConfiguration(config);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public NagiosConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared configuraiton. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(NagiosConfiguration configuration) {
        this.configuration = configuration;
    }


    public String getHost() {
        return configuration.getHost();
    }

    /**
     * This is the address of the Nagios host where checks should be send.
     * @param host
     */
    public void setHost(String host) {
        configuration.setHost(host);
    }

    public int getPort() {
        return configuration.getPort();
    }

    /**
     * The port number of the host.
     * @param port
     */
    public void setPort(int port) {
        configuration.setPort(port);
    }

    public int getConnectionTimeout() {
        return configuration.getConnectionTimeout();
    }

    /**
     * Connection timeout in millis.
     * @param connectionTimeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        configuration.setConnectionTimeout(connectionTimeout);
    }

    public int getTimeout() {
        return configuration.getTimeout();
    }

    /**
     * Sending timeout in millis.
     * @param timeout
     */
    public void setTimeout(int timeout) {
        configuration.setTimeout(timeout);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    /**
     * Password to be authenticated when sending checks to Nagios.
     * @param password
     */
    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public NagiosEncryptionMethod getEncryptionMethod() {
        return configuration.getEncryptionMethod();
    }

    /**
     * To specify an encryption method.
     * @param encryptionMethod
     */
    public void setEncryptionMethod(NagiosEncryptionMethod encryptionMethod) {
        configuration.setEncryptionMethod(encryptionMethod);
    }
}
