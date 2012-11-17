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

import java.util.List;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.jclouds.Context;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.compute.ComputeService;

/**
 * Represents the component that manages {@link JcloudsEndpoint}.
 */
public class JcloudsComponent extends DefaultComponent {

    private List<BlobStore> blobStores;
    private List<ComputeService> computeServices;

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
            if (uriParts != null && uriParts.length >= 2) {
                String provider = uriParts[1];
                BlobStore blobStore = getBlobStore(provider);
                endpoint = new JcloudsBlobStoreEndpoint(uri, this, blobStore);
            } else {
                throw new IllegalArgumentException("Invalid Endpoint URI: " + uri + ". It should contains a valid provider name");
            }
        } else if (JcloudsConstants.COMPUTE.endsWith(endpointType)) {
            if (uriParts != null && uriParts.length >= 2) {
                String provider = uriParts[1];
                ComputeService computeService = getComputeService(provider);
                endpoint = new JcloudsComputeEndpoint(uri, this, computeService);
            } else {
                throw new IllegalArgumentException("Invalid Endpoint URI: \" + uri + \". It should contains a valid provider name");
            }
        }

        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Returns the {@link BlobStore} that matches the given providerOrApi.
     * @param predicate The blobstore context name, provider or api.
     * @return The matching {@link BlobStore}
     */
    protected BlobStore getBlobStore(String predicate) throws IllegalArgumentException {
        if (blobStores != null && !blobStores.isEmpty()) {

            //First try using name and then fallback to the provider or api.
            if (isNameSupportedByContext()) {
                for (BlobStore blobStore : blobStores) {
                    if (blobStore.getContext().unwrap().getName().equals(predicate)) {
                        return blobStore;
                    }
                }
            }

            for (BlobStore blobStore : blobStores) {
                if (blobStore.getContext().unwrap().getId().equals(predicate)) {
                    return blobStore;
                }
            }
            throw new IllegalArgumentException(String.format("No blobstore found for:%s", predicate));
        } else {
            throw new IllegalArgumentException("No blobstore available.");
        }
    }

    /**
     * Returns the {@link ComputeService} that matches the given predicate.
     * @param predicate The compute context name, provider or api.
     * @return The matching {@link ComputeService}
     */
    protected ComputeService getComputeService(String predicate) throws IllegalArgumentException {

        if (computeServices != null && !computeServices.isEmpty()) {
            //First try using name and then fallback to the provider or api.
            if (isNameSupportedByContext()) {
                for (ComputeService computeService : computeServices) {
                    if (computeService.getContext().unwrap().getName().equals(predicate)) {
                        return computeService;
                    }
                }
            }

            for (ComputeService computeService : computeServices) {
                if (computeService.getContext().unwrap().getId().equals(predicate)) {
                    return computeService;
                }
            }
            throw new IllegalArgumentException(String.format("No compute service found for :%s", predicate));
        } else {
            throw new IllegalArgumentException("No compute service available.");
        }
    }

    /**
     * Checks if jclouds {@link Context} supports the name.
     * We need this method as getName is not supported in earlier micro version of 1.5.x.
     * So we use this check to fallback to traditional means of looking up contexts and services, if name is not present.
     */
    private boolean isNameSupportedByContext() {
        try {
            Context.class.getMethod("getName");
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    public List<BlobStore> getBlobStores() {
        return blobStores;
    }

    public void setBlobStores(List<BlobStore> blobStores) {
        this.blobStores = blobStores;
    }

    public List<ComputeService> getComputeServices() {
        return computeServices;
    }

    public void setComputeServices(List<ComputeService> computeServices) {
        this.computeServices = computeServices;
    }
}
