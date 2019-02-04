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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.Metadata;

/**
 * Sends the message to a dynamic endpoint
 * <p/>
 * You can specify multiple languages in the uri separated by the plus sign, such as <tt>mock:+language:xpath:/order/@uri</tt>
 * where <tt>mock:</tt> would be a prefix to a xpath expression.
 * <p/>
 * For more dynamic behavior use <a href="http://camel.apache.org/recipient-list.html">Recipient List</a> or
 * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router</a> EIP instead.
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "toD")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToDynamicDefinition extends NoOutputDefinition<ToDynamicDefinition> {

    @XmlAttribute @Metadata(required = true)
    private String uri;
    @XmlAttribute
    private ExchangePattern pattern;
    @XmlAttribute
    private Integer cacheSize;
    @XmlAttribute
    private Boolean ignoreInvalidEndpoint;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean allowOptimisedComponents;

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

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the optional {@link ExchangePattern} used to invoke this endpoint
     */
    public ToDynamicDefinition pattern(ExchangePattern pattern) {
        setPattern(pattern);
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.spi.ConsumerCache} which is used to cache and reuse producers.
     *
     * @param cacheSize  the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public ToDynamicDefinition cacheSize(int cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public ToDynamicDefinition ignoreInvalidEndpoint() {
        setIgnoreInvalidEndpoint(true);
        return this;
    }

    /**
     * Whether to allow components to optimise toD if they are {@link org.apache.camel.spi.SendDynamicAware}.
     *
     * @return the builder
     */
    public ToDynamicDefinition allowOptimisedComponents(boolean allowOptimisedComponents) {
        setAllowOptimisedComponents(allowOptimisedComponents);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getUri() {
        return uri;
    }

    /**
     * The uri of the endpoint to send to. The uri can be dynamic computed using the {@link org.apache.camel.language.simple.SimpleLanguage} expression.
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public Boolean getIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(Boolean ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }

    public Boolean getAllowOptimisedComponents() {
        return allowOptimisedComponents;
    }

    public void setAllowOptimisedComponents(Boolean allowOptimisedComponents) {
        this.allowOptimisedComponents = allowOptimisedComponents;
    }

}
