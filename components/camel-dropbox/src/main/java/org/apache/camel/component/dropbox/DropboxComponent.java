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
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link DropboxEndpoint}.
 */
public class DropboxComponent extends DefaultComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxComponent.class);

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DropboxConfiguration configuration = new DropboxConfiguration();

        // set options from component
        configuration.setAppKey((String) parameters.get("appKey"));
        configuration.setAppSecret((String)parameters.get("appSecret"));
        configuration.setAccessToken((String)parameters.get("accessToken"));
        configuration.setLocalPath((String)parameters.get("localPath"));
        configuration.setRemotePath((String)parameters.get("remotePath"));
        configuration.setNewRemotePath((String)parameters.get("newRemotePath"));
        configuration.setQuery((String)parameters.get("query"));
        configuration.setOperation(DropboxOperation.valueOf(remaining));

        // and then override from parameters
        setProperties(configuration, parameters);

        //create dropbox client
        configuration.createClient();

        LOG.debug("dropbox configuration set!");

        Endpoint endpoint = new DropboxEndpoint(uri,this,configuration);
        return endpoint;
    }

}
