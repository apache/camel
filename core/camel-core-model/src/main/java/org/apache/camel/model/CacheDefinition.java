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
package org.apache.camel.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;

/**
 * Caches the result of the nested processing steps using a read-through pattern.
 * <p/>
 * On cache hit (key found), the cached value is set as the message body and the nested steps are skipped. On cache
 * miss, the nested steps execute normally and the resulting message body is stored in the cache under the computed key.
 * <p/>
 * Cache errors are handled gracefully — they are logged but never propagate to the exchange. Failed exchanges (those
 * with an exception) are never cached.
 *
 * @since 4.23
 */
@Metadata(firstVersion = "4.23.0", label = "eip,routing,caching",
          description = "Caches the result of the nested processing steps."
                        + " On cache hit, skips the block and sets the body from cache."
                        + " On cache miss, executes the block and caches the result body.")
@XmlRootElement(name = "cache")
@XmlAccessorType(XmlAccessType.FIELD)
public class CacheDefinition extends OutputExpressionNode {

    @XmlTransient
    private KeyValueRepository keyValueRepositoryBean;

    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.spi.KeyValueRepository",
              description = "Sets the reference name of the KeyValueRepository to use as the cache backing store."
                            + " If not set, a MemoryKeyValueRepository is auto-created.")
    private String keyValueRepository;

    @XmlAttribute
    @Metadata(javaType = "java.time.Duration", defaultValue = "-1",
              description = "Sets the time-to-live for cached entries."
                            + " Supports duration syntax (e.g. 10m, 1h) or milliseconds."
                            + " Default: -1 (no expiration).")
    private String ttl;

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false",
              description = "Whether to cache null results."
                            + " By default, null message bodies are not cached.")
    private String cacheNull;

    public CacheDefinition() {
    }

    protected CacheDefinition(CacheDefinition source) {
        super(source);
        this.keyValueRepositoryBean = source.keyValueRepositoryBean;
        this.keyValueRepository = source.keyValueRepository;
        this.ttl = source.ttl;
        this.cacheNull = source.cacheNull;
    }

    public CacheDefinition(Expression cacheKeyExpression) {
        super(cacheKeyExpression);
    }

    @Override
    public CacheDefinition copyDefinition() {
        return new CacheDefinition(this);
    }

    @Override
    public String toString() {
        return "Cache[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "cache";
    }

    @Override
    public String getLabel() {
        return "cache[" + getExpression() + "]";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the reference name of the {@link KeyValueRepository} to use as the cache backing store.
     *
     * @param  ref the reference name of the KeyValueRepository in the registry
     * @return     builder
     */
    public CacheDefinition keyValueRepository(String ref) {
        setKeyValueRepository(ref);
        return this;
    }

    /**
     * Sets the {@link KeyValueRepository} instance to use as the cache backing store.
     *
     * @param  keyValueRepository the KeyValueRepository instance
     * @return                    builder
     */
    public CacheDefinition keyValueRepository(KeyValueRepository keyValueRepository) {
        this.keyValueRepositoryBean = keyValueRepository;
        return this;
    }

    /**
     * Sets the time-to-live for cached entries as a duration string (e.g. "10m", "1h") or milliseconds.
     *
     * @param  ttl the time-to-live duration
     * @return     builder
     */
    public CacheDefinition ttl(String ttl) {
        setTtl(ttl);
        return this;
    }

    /**
     * Sets the time-to-live for cached entries in milliseconds.
     *
     * @param  ttlMillis the time-to-live in milliseconds
     * @return           builder
     */
    public CacheDefinition ttl(long ttlMillis) {
        setTtl(Long.toString(ttlMillis));
        return this;
    }

    /**
     * Whether to cache null results. By default, null message bodies are not cached.
     *
     * @param  cacheNull true to cache null bodies
     * @return           builder
     */
    public CacheDefinition cacheNull(boolean cacheNull) {
        setCacheNull(Boolean.toString(cacheNull));
        return this;
    }

    @Override
    @Metadata(description = "Expression to compute the cache key."
                            + " Messages with the same key share the cached result.")
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    // Properties
    // -------------------------------------------------------------------------

    public KeyValueRepository getKeyValueRepositoryBean() {
        return keyValueRepositoryBean;
    }

    public String getKeyValueRepository() {
        return keyValueRepository;
    }

    public void setKeyValueRepository(String keyValueRepository) {
        this.keyValueRepository = keyValueRepository;
    }

    public void setKeyValueRepository(KeyValueRepository keyValueRepository) {
        this.keyValueRepositoryBean = keyValueRepository;
    }

    public String getTtl() {
        return ttl;
    }

    public void setTtl(String ttl) {
        this.ttl = ttl;
    }

    public String getCacheNull() {
        return cacheNull;
    }

    public void setCacheNull(String cacheNull) {
        this.cacheNull = cacheNull;
    }

}
