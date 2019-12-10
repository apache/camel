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
package org.apache.camel.component.atmos;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.atmos.util.AtmosOperation;
import org.apache.camel.component.atmos.validator.AtmosConfigurationValidator;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("atmos")
public class AtmosComponent extends DefaultComponent {

    @Metadata(label = "security", secret = true)
    private String fullTokenId;
    @Metadata(label = "security", secret = true)
    private String secretKey;
    @Metadata(label = "advanced")
    private String uri;
    @Metadata(label = "security")
    private boolean sslValidation;

    public AtmosComponent() {
    }

    public AtmosComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected AtmosEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        AtmosConfiguration configuration = new AtmosConfiguration();

        String name = null;
        String operation = remaining;

        String[] parts = remaining.split("/");
        if (parts.length > 1) {
            name = parts[0];
            operation = parts[1];
        }
        configuration.setName(name);
        configuration.setOperation(AtmosOperation.valueOf(operation));

        // set options from component
        configuration.setUri(parameters.get("uri") == null
                ? this.uri
                : (String) parameters.get("uri"));
        configuration.setSecretKey(parameters.get("secretKey") == null
                ? this.secretKey
                : (String) parameters.get("secretKey"));
        configuration.setLocalPath((String) parameters.get("localPath"));
        configuration.setRemotePath((String) parameters.get("remotePath"));
        configuration.setNewRemotePath((String) parameters.get("newRemotePath"));
        configuration.setQuery((String) parameters.get("query"));
        configuration.setFullTokenId(parameters.get("fullTokenId") == null
                ? this.fullTokenId
                : (String) parameters.get("fullTokenId"));
        configuration.setSslValidation(this.sslValidation);

        //pass validation test
        AtmosConfigurationValidator.validate(configuration);

        // and then override from parameters
        AtmosEndpoint endpoint = new AtmosEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public String getFullTokenId() {
        return fullTokenId;
    }

    /**
     * The token id to pass to the Atmos client
     */
    public void setFullTokenId(String fullTokenId) {
        this.fullTokenId = fullTokenId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * The secret key to pass to the Atmos client (should be base64 encoded)
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getUri() {
        return uri;
    }

    /**
     * The URI of the server for the Atmos client to connect to
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public boolean isSslValidation() {
        return sslValidation;
    }

    /**
     * Whether the Atmos client should perform SSL validation
     */
    public void setSslValidation(boolean sslValidation) {
        this.sslValidation = sslValidation;
    }
}
