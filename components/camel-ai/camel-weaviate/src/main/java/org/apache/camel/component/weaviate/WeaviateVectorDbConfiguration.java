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

import io.weaviate.client.WeaviateClient;
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
    @UriParam
    private String scheme;

    @Metadata(label = "producer",
              description = "Weaviate server host to connect to")
    @UriParam
    private String host;

    @Metadata(label = "producer",
              description = "Proxy host to connect to weaviate through")
    @UriParam
    private String proxyHost;

    @Metadata(label = "producer",
              description = "Proxy port to connect to weaviate through")
    @UriParam
    private Integer proxyPort;

    @Metadata(label = "producer",
              description = "Proxy scheme to connect to weaviate through")
    @UriParam
    private String proxyScheme;

    @Metadata(label = "producer",
              description = "API Key to authenticate to weaviate with", secret = true)
    @UriParam
    private String apiKey;

    @Metadata(autowired = true)
    private WeaviateClient client;

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

    /*
     * Get the proxy host used for connecting to weaviate.
     */
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Set the proxy host used to connect to weaviate.
     *
     * @param proxyHost proxy host
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /*
     * Get the proxy port used to connect to weaviate.
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    /*
     * Set the proxy port used to connect to weaviate.
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /*
     * Get the scheme (http/https/etc) used for connecting to proxy.
     */
    public String getProxyScheme() {
        return proxyScheme;
    }

    /**
     * Set the scheme used to for connecting to the proxy.
     *
     * @param proxyScheme
     */
    public void setProxyScheme(String proxyScheme) {
        this.proxyScheme = proxyScheme;
    }

    /**
     * Get the io.weaviate.client.WeaviateClient.
     */
    public WeaviateClient getClient() {
        return client;
    }

    /**
     * Set the io.weaviate.client.WeaviateClient used.
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
