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
package org.apache.camel.component.jclouds;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.BlobStoreContextFactory;

/**
 * Represents the component that manages {@link JcloudsEndpoint}.
 */
public class JcloudsComponent extends DefaultComponent {
    private String provider;
    private String identity;
    private String creadential;

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = null;

        String[] uriParts = null;
        String endpointType = null;

        if (remaining != null) {
            uriParts = remaining.split(JcloudsConstants.DELIMETER);
            if (uriParts != null && uriParts.length > 0) {
                endpointType = uriParts[0];
            }
        }

        if (JcloudsConstants.BLOBSTORE.endsWith(endpointType)) {

            if (uriParts.length >= 2) {
                String container = uriParts[1];
                BlobStoreContext blobStoreContext = new BlobStoreContextFactory().createContext(provider, identity, creadential);
                endpoint = new JcloudsBlobStoreEndpoint(uri, this, blobStoreContext, container);
            } else {
                throw new Exception("Invalid Endpoint URI. It should contains a valid container name");
            }
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getCreadential() {
        return creadential;
    }

    public void setCreadential(String creadential) {
        this.creadential = creadential;
    }
}
