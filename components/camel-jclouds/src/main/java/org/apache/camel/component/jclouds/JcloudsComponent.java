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
package org.apache.camel.component.jclouds;

import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.jclouds.Context;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.compute.ComputeService;

/**
 * Represents the component that manages {@link JcloudsEndpoint}.
 */
@Component("jclouds")
public class JcloudsComponent extends DefaultComponent {

    private List<BlobStore> blobStores;
    private List<ComputeService> computeServices;

    public JcloudsComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String[] uriParts = remaining.split(JcloudsConstants.DELIMETER);
        if (uriParts.length != 2) {
            throw new IllegalArgumentException("Invalid Endpoint URI: " + uri + ". It should contains a valid command and providerId");
        }
        String endpointType = uriParts[0];
        String providerId = uriParts[1];

        JcloudsCommand command = JcloudsCommand.valueOf(endpointType);

        JcloudsConfiguration configuration = new JcloudsConfiguration();
        configuration.setCommand(command);
        configuration.setProviderId(providerId);

        JcloudsEndpoint endpoint;
        if (JcloudsCommand.blobstore == command) {
            endpoint = new JcloudsBlobStoreEndpoint(uri, this, getBlobStore(providerId));
        } else {
            endpoint = new JcloudsComputeEndpoint(uri, this, getComputeService(providerId));
        }
        endpoint.setConfiguration(configuration);
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

    /**
     * To use the given BlobStore which must be configured when using blobstore.
     */
    public void setBlobStores(List<BlobStore> blobStores) {
        this.blobStores = blobStores;
    }

    public List<ComputeService> getComputeServices() {
        return computeServices;
    }

    /**
     * To use the given ComputeService which must be configured when use compute.
     */
    public void setComputeServices(List<ComputeService> computeServices) {
        this.computeServices = computeServices;
    }
}
