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

package org.apache.camel.component.langchain4j.embeddingstore;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for LangChain4j embedding store component.
 *
 * <p>
 * This configuration class defines the parameters needed to configure embedding store operations. It supports both
 * direct embedding store instances and factory-based creation for more dynamic scenarios.
 * </p>
 *
 * <p>
 * Either {@code embeddingStore} or {@code embeddingStoreFactory} should be configured, with factory taking precedence
 * if both are present.
 * </p>
 */
@Configurer
@UriParams
public class LangChain4jEmbeddingStoreConfiguration implements Cloneable {

    /** Direct embedding store instance for vector operations */
    @Metadata(autowired = true)
    @UriParam
    private EmbeddingStore<TextSegment> embeddingStore;

    /** Factory for creating embedding stores dynamically, takes precedence over direct store */
    @UriParam(
            description =
                    "The embedding store factory to use for creating embedding stores if no embeddingstore is provided")
    @Metadata(autowired = true)
    private EmbeddingStoreFactory embeddingStoreFactory;

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    /**
     * Sets the embedding store to use
     */
    public void setEmbeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    /**
     * Gets the embedding store factory for dynamic store creation.
     *
     * @return the instance of the embedding store factory in use
     */
    public EmbeddingStoreFactory getEmbeddingStoreFactory() {
        return embeddingStoreFactory;
    }

    public void setEmbeddingStoreFactory(EmbeddingStoreFactory embeddingStoreFactory) {
        this.embeddingStoreFactory = embeddingStoreFactory;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public LangChain4jEmbeddingStoreConfiguration copy() {
        try {
            return (LangChain4jEmbeddingStoreConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
