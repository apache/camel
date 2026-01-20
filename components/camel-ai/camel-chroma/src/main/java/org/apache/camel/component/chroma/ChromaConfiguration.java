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
package org.apache.camel.component.chroma;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;

@Configurer
@UriParams
public class ChromaConfiguration implements Cloneable {

    @UriParam
    private String host;

    @UriParam(defaultValue = "10")
    private int maxResults = 10;

    @Metadata(autowired = true)
    private Client client;

    @Metadata(autowired = true)
    private EmbeddingFunction embeddingFunction;

    public String getHost() {
        return host;
    }

    /**
     * The Chroma server host URL.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getMaxResults() {
        return maxResults;
    }

    /**
     * Max results for similarity search.
     */
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public Client getClient() {
        return client;
    }

    /**
     * Reference to a `tech.amikos.chromadb.Client`.
     */
    public void setClient(Client client) {
        this.client = client;
    }

    public EmbeddingFunction getEmbeddingFunction() {
        return embeddingFunction;
    }

    /**
     * Reference to a `tech.amikos.chromadb.embeddings.EmbeddingFunction`. Used to generate embeddings from documents.
     * If not provided, embeddings must be passed directly via headers.
     */
    public void setEmbeddingFunction(EmbeddingFunction embeddingFunction) {
        this.embeddingFunction = embeddingFunction;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public ChromaConfiguration copy() {
        try {
            return (ChromaConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
