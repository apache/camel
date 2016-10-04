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
package org.apache.camel.component.azure.storage;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link StorageQueueEndpoint}.
 */
public class StorageQueueComponent extends UriEndpointComponent {
    
    public StorageQueueComponent() {
        super(StorageQueueEndpoint.class);
    }

    public StorageQueueComponent(CamelContext context) {
        super(context, StorageQueueEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        StorageConfiguration configuration = new StorageConfiguration();
        setProperties(configuration, parameters);

        configuration.setResource(remaining);

        StorageQueueEndpoint endpoint = new StorageQueueEndpoint(uri, this, configuration);
        return endpoint;
    }
}
