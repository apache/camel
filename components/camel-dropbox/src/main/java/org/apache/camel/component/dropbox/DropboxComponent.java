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
package org.apache.camel.component.dropbox;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.dropbox.util.DropboxOperation;
import org.apache.camel.component.dropbox.util.DropboxPropertyManager;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.camel.component.dropbox.validator.DropboxConfigurationValidator;
import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DropboxComponent extends UriEndpointComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxComponent.class);

    public DropboxComponent() {
        super(DropboxEndpoint.class);
    }

    /**
     * Create a camel endpoint after passing validation on the incoming url.
     * @param uri the full URI of the endpoint
     * @param remaining the remaining part of the URI without the query
     *                parameters or component prefix
     * @param parameters the optional parameters passed in
     * @return the camel endpoint
     * @throws Exception
     */
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DropboxConfiguration configuration = new DropboxConfiguration();

        // set options from component
        configuration.setAccessToken((String)parameters.get("accessToken"));
        configuration.setLocalPath((String)parameters.get("localPath"));
        configuration.setRemotePath(
                parameters.get("remotePath") != null
                    ? ((String) parameters.get("remotePath")).replaceAll("\\s", "+")
                    : null);
        configuration.setNewRemotePath((String)parameters.get("newRemotePath"));
        configuration.setQuery((String)parameters.get("query"));
        configuration.setOperation(DropboxOperation.valueOf(remaining));
        configuration.setClientIdentifier(
                parameters.get("clientIdentifier") == null
                        ? DropboxPropertyManager.getInstance().getProperty("clientIdentifier")
                        : (String) parameters.get("clientIdentifier"));
        if (parameters.get("uploadMode") != null) {
            configuration.setUploadMode(DropboxUploadMode.valueOf((String)parameters.get("uploadMode")));
        }


        //pass validation test
        DropboxConfigurationValidator.validateCommonProperties(configuration);

        // and then override from parameters
        setProperties(configuration, parameters);

        return new DropboxEndpoint(uri, this, configuration);
    }

}
