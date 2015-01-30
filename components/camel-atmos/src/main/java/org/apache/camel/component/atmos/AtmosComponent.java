/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.camel.component.atmos;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.atmos.util.AtmosOperation;
import org.apache.camel.component.atmos.util.AtmosPropertyManager;
import org.apache.camel.component.atmos.validator.AtmosConfigurationValidator;
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtmosComponent extends DefaultComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(AtmosComponent.class);

    /**
     * Create a camel endpoint after passing validation on the incoming url.
     *
     * @param uri the full URI of the endpoint
     * @param remaining the remaining part of the URI without the query
     * parameters or component prefix
     * @param parameters the optional parameters passed in
     * @return the camel endpoint
     * @throws Exception
     */
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        AtmosConfiguration configuration = new AtmosConfiguration();

        // set options from component
        configuration.setUri((String) parameters.get("uri") == null
                ? AtmosPropertyManager.getInstance().getProperty("uri")
                : (String) parameters.get("uri"));
        configuration.setSecretKey((String) parameters.get("secretKey") == null
                ? AtmosPropertyManager.getInstance().getProperty("secretKey")
                : (String) parameters.get("secretKey"));
        configuration.setLocalPath((String) parameters.get("localPath"));
        configuration.setRemotePath((String) parameters.get("remotePath"));
        configuration.setNewRemotePath((String) parameters.get("newRemotePath"));
        configuration.setQuery((String) parameters.get("query"));
        configuration.setOperation(AtmosOperation.valueOf(remaining));
        configuration.setFullTokenId(parameters.get("fullTokenId") == null
                ? AtmosPropertyManager.getInstance().getProperty("fullTokenId")
                : (String) parameters.get("fullTokenId"));
        configuration.setEnableSslValidation(Boolean.parseBoolean((String) AtmosPropertyManager.getInstance().getProperty("sslValidation")));

        //pass validation test
        AtmosConfigurationValidator.validate(configuration);

        // and then override from parameters
        setProperties(configuration, parameters);

        LOG.info("atmos configuration set!");

        Endpoint endpoint = new AtmosEndpoint(uri, this, configuration);
        return endpoint;
    }

}
