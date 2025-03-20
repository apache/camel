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
package org.apache.camel.component.infinispan.remote;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.infinispan.api.annotations.indexing.option.VectorSimilarity;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;

@UriParams
public class InfinispanRemoteConfiguration extends InfinispanConfiguration implements Cloneable {
    @UriParam
    private String hosts;
    @UriParam(label = "common,security", defaultValue = "false")
    private boolean secure;
    @UriParam(label = "common,security")
    private String username;
    @UriParam(label = "common,security", secret = true)
    private String password;
    @UriParam(label = "common,security")
    private String saslMechanism;
    @UriParam(label = "common,security")
    private String securityRealm;
    @UriParam(label = "common,security")
    private String securityServerName;
    @Metadata(autowired = true)
    @UriParam(label = "advanced")
    private Configuration cacheContainerConfiguration;
    @Metadata(autowired = true)
    @UriParam(label = "advanced")
    private RemoteCacheManager cacheContainer;
    @UriParam(label = "advanced")
    private Map<String, String> configurationProperties;
    @UriParam(label = "consumer")
    private String eventTypes;
    @UriParam(label = "consumer")
    private InfinispanRemoteCustomListener customListener;
    @UriParam(label = "advanced", javaType = "java.lang.String")
    private Flag[] flags;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean embeddingStoreEnabled = true;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean embeddingStoreRegisterSchema = true;
    @UriParam(label = "producer,advanced")
    private int embeddingStoreDimension;
    @UriParam(label = "producer,advanced", defaultValue = "COSINE")
    private VectorSimilarity embeddingStoreVectorSimilarity = VectorSimilarity.COSINE;
    @UriParam(label = "producer,advanced", defaultValue = "3")
    private int embeddingStoreDistance = 3;
    @UriParam(label = "producer,advanced")
    private String embeddingStoreTypeName;

    public Configuration getCacheContainerConfiguration() {
        return cacheContainerConfiguration;
    }

    /**
     * The CacheContainer configuration. Used if the cacheContainer is not defined.
     */
    public void setCacheContainerConfiguration(Configuration cacheContainerConfiguration) {
        this.cacheContainerConfiguration = cacheContainerConfiguration;
    }

    /**
     * Specifies the cache Container to connect
     */
    public RemoteCacheManager getCacheContainer() {
        return cacheContainer;
    }

    public void setCacheContainer(RemoteCacheManager cacheContainer) {
        this.cacheContainer = cacheContainer;
    }

    /**
     * Specifies the host of the cache on Infinispan instance. Multiple hosts can be separated by semicolon.
     */
    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public boolean isSecure() {
        return secure;
    }

    /**
     * Define if we are connecting to a secured Infinispan instance
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Define the username to access the infinispan instance
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Define the password to access the infinispan instance
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getSaslMechanism() {
        return saslMechanism;
    }

    /**
     * Define the SASL Mechanism to access the infinispan instance
     */
    public void setSaslMechanism(String saslMechanism) {
        this.saslMechanism = saslMechanism;
    }

    public String getSecurityRealm() {
        return securityRealm;
    }

    /**
     * Define the security realm to access the infinispan instance
     */
    public void setSecurityRealm(String securityRealm) {
        this.securityRealm = securityRealm;
    }

    public String getSecurityServerName() {
        return securityServerName;
    }

    /**
     * Define the security server name to access the infinispan instance
     */
    public void setSecurityServerName(String securityServerName) {
        this.securityServerName = securityServerName;
    }

    public Map<String, String> getConfigurationProperties() {
        return configurationProperties;
    }

    /**
     * Implementation specific properties for the CacheManager
     */
    public void setConfigurationProperties(Map<String, String> configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

    /**
     * Adds an implementation specific property for the CacheManager
     */
    public void addConfigurationProperty(String key, String value) {
        if (this.configurationProperties == null) {
            this.configurationProperties = new HashMap<>();
        }

        this.configurationProperties.put(key, value);
    }

    public String getEventTypes() {
        return eventTypes;
    }

    /**
     * Specifies the set of event types to register by the consumer.Multiple event can be separated by comma.
     * <p/>
     * The possible event types are: CLIENT_CACHE_ENTRY_CREATED, CLIENT_CACHE_ENTRY_MODIFIED,
     * CLIENT_CACHE_ENTRY_REMOVED, CLIENT_CACHE_ENTRY_EXPIRED, CLIENT_CACHE_FAILOVER
     */
    public void setEventTypes(String eventTypes) {
        this.eventTypes = eventTypes;
    }

    /**
     * Returns the custom listener in use, if provided
     */
    public InfinispanRemoteCustomListener getCustomListener() {
        return customListener;
    }

    public void setCustomListener(InfinispanRemoteCustomListener customListener) {
        this.customListener = customListener;
    }

    public boolean hasCustomListener() {
        return customListener != null;
    }

    public Flag[] getFlags() {
        return flags;
    }

    /**
     * A comma separated list of org.infinispan.client.hotrod.Flag to be applied by default on each cache invocation.
     */
    public void setFlags(String flagsAsString) {
        String[] flagsArray = flagsAsString.split(",");
        this.flags = new Flag[flagsArray.length];

        for (int i = 0; i < flagsArray.length; i++) {
            this.flags[i] = Flag.valueOf(flagsArray[i]);
        }
    }

    public void setFlags(Flag... flags) {
        this.flags = flags;
    }

    public boolean hasFlags() {
        return flags != null && flags.length > 0;
    }

    /**
     * The dimension size used to store vector embeddings. This should be equal to the dimension size of the model used
     * to create the vector embeddings. This option is mandatory if the embedding store is enabled.
     */
    public void setEmbeddingStoreDimension(int embeddingStoreDimension) {
        this.embeddingStoreDimension = embeddingStoreDimension;
    }

    public long getEmbeddingStoreDimension() {
        return embeddingStoreDimension;
    }

    /**
     * The vector similarity algorithm used to store embeddings.
     */
    public void setEmbeddingStoreVectorSimilarity(VectorSimilarity embeddingStoreVectorSimilarity) {
        this.embeddingStoreVectorSimilarity = embeddingStoreVectorSimilarity;
    }

    public VectorSimilarity getEmbeddingStoreVectorSimilarity() {
        return embeddingStoreVectorSimilarity;
    }

    /**
     * Whether to enable the embedding store. When enabled, the embedding store will be configured automatically when
     * Camel starts. Note that this feature requires camel-langchain4j-embeddings to be on the classpath.
     */
    public void setEmbeddingStoreEnabled(boolean embeddingStoreEnabled) {
        this.embeddingStoreEnabled = embeddingStoreEnabled;
    }

    public boolean isEmbeddingStoreEnabled() {
        return embeddingStoreEnabled;
    }

    /**
     * Whether to automatically register the proto schema for the types required by embedding store cache put and query
     * operations.
     */
    public void setEmbeddingStoreRegisterSchema(boolean embeddingStoreRegisterSchema) {
        this.embeddingStoreRegisterSchema = embeddingStoreRegisterSchema;
    }

    public boolean isEmbeddingStoreRegisterSchema() {
        return embeddingStoreRegisterSchema;
    }

    /**
     * The distance to use for kNN search queries in relation to the configured vector similarity.
     */
    public void setEmbeddingStoreDistance(int embeddingStoreDistance) {
        this.embeddingStoreDistance = embeddingStoreDistance;
    }

    public int getEmbeddingStoreDistance() {
        return embeddingStoreDistance;
    }

    /**
     * The name of the type used to store embeddings. The default is 'InfinispanRemoteEmbedding' suffixed with the value
     * of the embeddingStoreDimension option. E.g. CamelInfinispanRemoteEmbedding384.
     */
    public void setEmbeddingStoreTypeName(String embeddingStoreTypeName) {
        this.embeddingStoreTypeName = embeddingStoreTypeName;
    }

    public String getEmbeddingStoreTypeName() {
        return embeddingStoreTypeName;
    }

    @Override
    public InfinispanRemoteConfiguration clone() {
        try {
            return (InfinispanRemoteConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
