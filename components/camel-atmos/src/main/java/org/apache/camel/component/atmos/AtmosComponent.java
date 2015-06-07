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
package org.apache.camel.component.atmos;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.atmos.util.AtmosOperation;
import org.apache.camel.component.atmos.util.AtmosPropertyManager;
import org.apache.camel.component.atmos.validator.AtmosConfigurationValidator;
import org.apache.camel.impl.UriEndpointComponent;

public class AtmosComponent extends UriEndpointComponent {

    public AtmosComponent() {
        super(AtmosEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
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
                ? AtmosPropertyManager.getInstance().getProperty("uri")
                : (String) parameters.get("uri"));
        configuration.setSecretKey(parameters.get("secretKey") == null
                ? AtmosPropertyManager.getInstance().getProperty("secretKey")
                : (String) parameters.get("secretKey"));
        configuration.setLocalPath((String) parameters.get("localPath"));
        configuration.setRemotePath((String) parameters.get("remotePath"));
        configuration.setNewRemotePath((String) parameters.get("newRemotePath"));
        configuration.setQuery((String) parameters.get("query"));
        configuration.setFullTokenId(parameters.get("fullTokenId") == null
                ? AtmosPropertyManager.getInstance().getProperty("fullTokenId")
                : (String) parameters.get("fullTokenId"));
        configuration.setEnableSslValidation(Boolean.parseBoolean(AtmosPropertyManager.getInstance().getProperty("sslValidation")));

        //pass validation test
        AtmosConfigurationValidator.validate(configuration);

        // and then override from parameters
        setProperties(configuration, parameters);

        Endpoint endpoint = new AtmosEndpoint(uri, this, configuration);
        return endpoint;
    }

}
