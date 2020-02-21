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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Sends the message to a dynamic endpoint
 * <p/>
 * You can specify multiple languages in the uri separated by the plus sign,
 * such as <tt>mock:+language:xpath:/order/@uri</tt> where <tt>mock:</tt> would
 * be a prefix to a xpath expression.
 * <p/>
 * For more dynamic behavior use
 * <a href="http://camel.apache.org/recipient-list.html">Recipient List</a> or
 * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router</a> EIP
 * instead.
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "toD")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDynamicDefinition extends NoOutputDefinition<ToDynamicDefinition> {

    @XmlTransient
    protected EndpointProducerBuilder endpointProducerBuilder;
    @XmlAttribute
    @Metadata(required = true)
    private String uri;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.ExchangePattern", enums = "InOnly,InOut,InOptionalOut")
    private String pattern;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String cacheSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreInvalidEndpoint;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String allowOptimisedComponents;

    public ToDynamicDefinition() {
    }

    public ToDynamicDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public String getShortName() {
        return "toD";
    }

    @Override
    public String toString() {
        return "DynamicTo[" + getLabel() + "]";
    }

    @Override
    public String getLabel() {
        return uri;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public ToDynamicDefinition pattern(ExchangePattern pattern) {
        return pattern(pattern.name());
    }

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public ToDynamicDefinition pattern(String pattern) {
        setPattern(pattern);
        return this;
    }

    /**
     * Sets the maximum size used by the
     * {@link org.apache.camel.spi.ProducerCache} which is used to cache and
     * reuse producers when using this recipient list, when uris are reused.
     *
     * Beware that when using dynamic endpoints then it affects how well the cache can be utilized.
     * If each dynamic endpoint is unique then its best to turn of caching by setting this to -1, which
     * allows Camel to not cache both the producers and endpoints; they are regarded as prototype scoped
     * and will be stopped and discarded after use. This reduces memory usage as otherwise producers/endpoints
     * are stored in memory in the caches.
     *
     * However if there are a high degree of dynamic endpoints that have been used before, then it can
     * benefit to use the cache to reuse both producers and endpoints and therefore the cache size
     * can be set accordingly or rely on the default size (1000).
     *
     * If there is a mix of unique and used before dynamic endpoints, then setting a reasonable cache size
     * can help reduce memory usage to avoid storing too many non frequent used producers.
     *
     * @param cacheSize the cache size, use <tt>0</tt> for default cache size,
     *            or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public ToDynamicDefinition cacheSize(int cacheSize) {
        return cacheSize(Integer.toString(cacheSize));
    }

    /**
     * Sets the maximum size used by the
     * {@link org.apache.camel.spi.ProducerCache} which is used to cache and
     * reuse producers when using this recipient list, when uris are reused.
     *
     * Beware that when using dynamic endpoints then it affects how well the cache can be utilized.
     * If each dynamic endpoint is unique then its best to turn of caching by setting this to -1, which
     * allows Camel to not cache both the producers and endpoints; they are regarded as prototype scoped
     * and will be stopped and discarded after use. This reduces memory usage as otherwise producers/endpoints
     * are stored in memory in the caches.
     *
     * However if there are a high degree of dynamic endpoints that have been used before, then it can
     * benefit to use the cache to reuse both producers and endpoints and therefore the cache size
     * can be set accordingly or rely on the default size (1000).
     *
     * If there is a mix of unique and used before dynamic endpoints, then setting a reasonable cache size
     * can help reduce memory usage to avoid storing too many non frequent used producers.
     *
     * @param cacheSize the cache size, use <tt>0</tt> for default cache size,
     *            or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public ToDynamicDefinition cacheSize(String cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer
     * with that endpoint
     *
     * @return the builder
     */
    public ToDynamicDefinition ignoreInvalidEndpoint(boolean ignoreInvalidEndpoint) {
        return ignoreInvalidEndpoint(Boolean.toString(ignoreInvalidEndpoint));
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer
     * with that endpoint
     *
     * @return the builder
     */
    public ToDynamicDefinition ignoreInvalidEndpoint(String ignoreInvalidEndpoint) {
        setIgnoreInvalidEndpoint(ignoreInvalidEndpoint);
        return this;
    }

    /**
     * Whether to allow components to optimise toD if they are
     * {@link org.apache.camel.spi.SendDynamicAware}.
     *
     * @return the builder
     */
    public ToDynamicDefinition allowOptimisedComponents() {
        return allowOptimisedComponents(true);
    }

    /**
     * Whether to allow components to optimise toD if they are
     * {@link org.apache.camel.spi.SendDynamicAware}.
     *
     * @return the builder
     */
    public ToDynamicDefinition allowOptimisedComponents(boolean allowOptimisedComponents) {
        return allowOptimisedComponents(Boolean.toString(allowOptimisedComponents));
    }

    /**
     * Whether to allow components to optimise toD if they are
     * {@link org.apache.camel.spi.SendDynamicAware}.
     *
     * @return the builder
     */
    public ToDynamicDefinition allowOptimisedComponents(String allowOptimisedComponents) {
        setAllowOptimisedComponents(allowOptimisedComponents);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getUri() {
        return uri;
    }

    /**
     * The uri of the endpoint to send to. The uri can be dynamic computed using
     * the {@link org.apache.camel.language.simple.SimpleLanguage} expression.
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public EndpointProducerBuilder getEndpointProducerBuilder() {
        return endpointProducerBuilder;
    }

    public void setEndpointProducerBuilder(EndpointProducerBuilder endpointProducerBuilder) {
        this.endpointProducerBuilder = endpointProducerBuilder;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(String cacheSize) {
        this.cacheSize = cacheSize;
    }

    public String getIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(String ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }

    public String getAllowOptimisedComponents() {
        return allowOptimisedComponents;
    }

    public void setAllowOptimisedComponents(String allowOptimisedComponents) {
        this.allowOptimisedComponents = allowOptimisedComponents;
    }

}
