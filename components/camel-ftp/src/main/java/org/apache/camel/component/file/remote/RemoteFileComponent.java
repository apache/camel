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
package org.apache.camel.component.file.remote;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.commons.net.ftp.FTPClientConfig;

public class RemoteFileComponent extends DefaultComponent<RemoteFileExchange> {
    private RemoteFileConfiguration configuration;

    public RemoteFileComponent() {
        this.configuration = new RemoteFileConfiguration();
    }

    public RemoteFileComponent(RemoteFileConfiguration configuration) {
        this.configuration = configuration;
    }

    public RemoteFileComponent(CamelContext context) {
        super(context);
        this.configuration = new RemoteFileConfiguration();
    }

    public String toString() {
        return "RemoteFileComponent";
    }

    public static RemoteFileComponent remoteFileComponent() {
        return new RemoteFileComponent();
    }

    protected RemoteFileEndpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        RemoteFileConfiguration config = getConfiguration().copy();

        // get the base uri part before the options as they can be non URI valid such as the expression using $ chars
        // and the URI constructor will regard $ as an illegal character and we dont want to enforce end users to
        // to espace the $ for the expression (file language)
        String baseUri = uri;
        if (uri.indexOf("?") != -1) {
            baseUri = uri.substring(0, uri.indexOf("?"));
        }
        config.configure(new URI(baseUri));

        // lets make sure we copy the configuration as each endpoint can
        // customize its own version
        final RemoteFileEndpoint endpoint;
        if ("ftp".equals(config.getProtocol())) {
            endpoint = new FtpEndpoint(uri, this, config);
        } else if ("sftp".equals(config.getProtocol())) {
            endpoint = new SftpEndpoint(uri, this, config);
        } else {
            throw new RuntimeCamelException("Unsupported protocol: " + config.getProtocol());
        }

        configureFTPClientConfig(parameters, endpoint);
        setProperties(endpoint.getConfiguration(), parameters);
        return endpoint;
    }

    private void configureFTPClientConfig(Map parameters, RemoteFileEndpoint endpoint) {
        // lookup client config in registry if provided
        String ref = getAndRemoveParameter(parameters, "ftpClientConfig", String.class);
        if (ref != null) {
            FTPClientConfig ftpClientConfig = this.getCamelContext().getRegistry().lookup(ref, FTPClientConfig.class);
            if (ftpClientConfig == null) {
                throw new IllegalArgumentException("FTPClientConfig " + ref + " not found in registry.");
            }
            endpoint.getConfiguration().setFtpClientConfig(ftpClientConfig);
        }
    }

    public RemoteFileConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(RemoteFileConfiguration configuration) {
        this.configuration = configuration;
    }
}
