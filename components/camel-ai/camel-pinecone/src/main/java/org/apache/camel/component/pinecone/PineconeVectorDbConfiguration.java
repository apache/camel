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
package org.apache.camel.component.pinecone;

import io.pinecone.clients.Pinecone;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class PineconeVectorDbConfiguration implements Cloneable {

    @Metadata(secret = true)
    @UriParam
    private String token;

    @Metadata(autowired = true)
    private Pinecone client;

    @UriParam(label = "producer")
    private String indexName;

    @UriParam(label = "producer")
    private String collectionSimilarityMetric;

    @UriParam(label = "producer")
    private Integer collectionDimension;

    @UriParam(label = "producer")
    private String cloud;

    @UriParam(label = "producer")
    private String cloudRegion;

    @UriParam(label = "producer")
    private String proxyHost;

    @UriParam(label = "producer")
    private Integer proxyPort;

    @UriParam(defaultValue = "true", label = "producer")
    private boolean tls;

    public String getIndexName() {
        return indexName;
    }

    /**
     * Sets the index name to use
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getCollectionSimilarityMetric() {
        return collectionSimilarityMetric;
    }

    /**
     * Sets the Collection Similarity Metric to use (cosine/euclidean/dotproduct)
     */
    public void setCollectionSimilarityMetric(String collectionSimilarityMetric) {
        this.collectionSimilarityMetric = collectionSimilarityMetric;
    }

    public Integer getCollectionDimension() {
        return collectionDimension;
    }

    /**
     * Sets the Collection Dimension to use (1-1536)
     */
    public void setCollectionDimension(Integer collectionDimension) {
        this.collectionDimension = collectionDimension;
    }

    public String getCloud() {
        return cloud;
    }

    /**
     * Sets the cloud type to use (aws/gcp/azure)
     */
    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getCloudRegion() {
        return cloudRegion;
    }

    /**
     * Sets the cloud region
     */
    public void setCloudRegion(String cloudRegion) {
        this.cloudRegion = cloudRegion;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Set the proxy host
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * Set the proxy port
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isTls() {
        return tls;
    }

    /**
     * Whether the client uses Transport Layer Security (TLS) to secure communications
     */
    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public String getToken() {
        return token;
    }

    /**
     * Sets the API key to use for authentication
     */
    public void setToken(String token) {
        this.token = token;
    }

    public Pinecone getClient() {
        return client;
    }

    /**
     * Reference to a `io.pinecone.clients.Pinecone`.
     */
    public void setClient(Pinecone client) {
        this.client = client;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public PineconeVectorDbConfiguration copy() {
        try {
            return (PineconeVectorDbConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
