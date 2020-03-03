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
package org.apache.camel.component.ssh;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link SshEndpoint}.
 */
@Component("ssh")
public class SshComponent extends DefaultComponent {
    @Metadata(label = "advanced")
    private SshConfiguration configuration = new SshConfiguration();

    public SshComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI u = new URI(uri);
        SshConfiguration newConfig = configuration.copy();
        newConfig.configure(u);

        SshEndpoint endpoint = new SshEndpoint(uri, this, newConfig);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public SshConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(SshConfiguration configuration) {
        this.configuration = configuration;
    }
}
