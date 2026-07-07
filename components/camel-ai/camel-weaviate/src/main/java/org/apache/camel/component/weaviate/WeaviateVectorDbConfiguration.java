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
package org.apache.camel.component.weaviate;

import io.weaviate.client6.v1.api.WeaviateClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class WeaviateVectorDbConfiguration implements Cloneable {

    @Metadata(label = "producer",
              description = "Scheme used to connect to weaviate")
    @UriParam(defaultValue = "http")
    private String scheme = "http";

    @Metadata(label = "producer",
              description = "Weaviate server host to connect to")
    @UriParam
    private String host;

    @Metadata(label = "producer",
              description = "gRPC host for Weaviate server connection")
    @UriParam
    private String grpcHost;

    @Metadata(label = "producer",
              description = "gRPC port for Weaviate server connection", defaultValue = "50051")
    @UriParam(defaultValue = "50051")
    private Integer grpcPort = 50051;

    @Metadata(label = "producer",
              description = "API Key to authenticate to weaviate with", security = "secret")
    @UriParam
    private String apiKey;

    @Metadata(autowired = true)
    private WeaviateClient client;

    /**
     * Get the gRPC host for Weaviate server connection.
     */
    public String getGrpcHost() {
        return grpcHost;
    }

    /**
     * Set the gRPC host for Weaviate server connection.
     */
    public void setGrpcHost(String grpcHost) {
        this.grpcHost = grpcHost;
    }

    /**
     * Get the gRPC port for Weaviate server connection.
     */
    public Integer getGrpcPort() {
        return grpcPort;
    }

    /**
     * Set the gRPC port for Weaviate server connection.
     */
    public void setGrpcPort(Integer grpcPort) {
        this.grpcPort = grpcPort;
    }

    /*
     * Get the api key used to authenticate to weaviate server.
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Set the api key used to authenticate to weaviate server.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Get the scheme (http/https/etc) used in connecting to weaviate.
     *
     * @return scheme
     */
    public String getScheme() {
        return scheme;
    }

    /*
     * Set the scheme (http/https/etc) used in connecting to weaviate.
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Get the weaviate server host.
     *
     * @return host
     */
    public String getHost() {
        return host;
    }

    /*
     * Set the weaviate server host.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Get the io.weaviate.client6.v1.api.WeaviateClient.
     */
    public WeaviateClient getClient() {
        return client;
    }

    /**
     * Set the io.weaviate.client6.v1.api.WeaviateClient used.
     *
     * @param client
     */
    public void setClient(WeaviateClient client) {
        this.client = client;
    }

    // ************************
    //
    // Clone
    //
    // ************************
    public WeaviateVectorDbConfiguration copy() {
        try {
            return (WeaviateVectorDbConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
