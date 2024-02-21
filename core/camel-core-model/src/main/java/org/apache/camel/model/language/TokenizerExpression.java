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
package org.apache.camel.model.language;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Tokenize text payloads using delimiter patterns.
 */
@Metadata(firstVersion = "2.0.0", label = "language,core", title = "Tokenize")
@XmlRootElement(name = "tokenize")
@XmlAccessorType(XmlAccessType.FIELD)
public class TokenizerExpression extends SingleInputTypedExpressionDefinition {

    @XmlAttribute(required = true)
    private String token;
    @XmlAttribute
    private String endToken;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String inheritNamespaceTagName;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String regex;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String xml;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String includeTokens;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String group;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String groupDelimiter;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String skipFirst;

    public TokenizerExpression() {
    }

    public TokenizerExpression(String token) {
        this.token = token;
    }

    private TokenizerExpression(Builder builder) {
        super(builder);
        this.token = builder.token;
        this.endToken = builder.endToken;
        this.inheritNamespaceTagName = builder.inheritNamespaceTagName;
        this.regex = builder.regex;
        this.xml = builder.xml;
        this.includeTokens = builder.includeTokens;
        this.group = builder.group;
        this.groupDelimiter = builder.groupDelimiter;
        this.skipFirst = builder.skipFirst;
    }

    @Override
    public String getLanguage() {
        return "tokenize";
    }

    public String getToken() {
        return token;
    }

    /**
     * The (start) token to use as tokenizer, for example you can use the new line token. You can use simple language as
     * the token to support dynamic tokens.
     */
    public void setToken(String token) {
        this.token = token;
    }

    public String getEndToken() {
        return endToken;
    }

    /**
     * The end token to use as tokenizer if using start/end token pairs. You can use simple language as the token to
     * support dynamic tokens.
     */
    public void setEndToken(String endToken) {
        this.endToken = endToken;
    }

    /**
     * If the token is a regular expression pattern.
     * <p/>
     * The default value is false
     */
    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getRegex() {
        return regex;
    }

    public String getInheritNamespaceTagName() {
        return inheritNamespaceTagName;
    }

    /**
     * To inherit namespaces from a root/parent tag name when using XML You can use simple language as the tag name to
     * support dynamic names.
     */
    public void setInheritNamespaceTagName(String inheritNamespaceTagName) {
        this.inheritNamespaceTagName = inheritNamespaceTagName;
    }

    public String getXml() {
        return xml;
    }

    /**
     * Whether the input is XML messages. This option must be set to true if working with XML payloads.
     */
    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getIncludeTokens() {
        return includeTokens;
    }

    /**
     * Whether to include the tokens in the parts when using pairs. When including tokens then the endToken property
     * must also be configured (to use pair mode).
     * <p/>
     * The default value is false
     */
    public void setIncludeTokens(String includeTokens) {
        this.includeTokens = includeTokens;
    }

    public String getGroup() {
        return group;
    }

    /**
     * To group N parts together, for example to split big files into chunks of 1000 lines. You can use simple language
     * as the group to support dynamic group sizes.
     */
    public void setGroup(String group) {
        this.group = group;
    }

    public String getGroupDelimiter() {
        return groupDelimiter;
    }

    /**
     * Sets the delimiter to use when grouping. If this has not been set then token will be used as the delimiter.
     */
    public void setGroupDelimiter(String groupDelimiter) {
        this.groupDelimiter = groupDelimiter;
    }

    public String getSkipFirst() {
        return skipFirst;
    }

    /**
     * To skip the very first element
     */
    public void setSkipFirst(String skipFirst) {
        this.skipFirst = skipFirst;
    }

    public String toString() {
        if (endToken != null) {
            return "tokenize{body() using tokens: " + token + "..." + endToken + "}";
        } else {
            String s = getSource();
            if (s == null) {
                s = "body";
            }
            return "tokenize{" + s + " using token: " + token + "}";
        }
    }

    /**
     * {@code Builder} is a specific builder for {@link TokenizerExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, TokenizerExpression> {

        private String token;
        private String endToken;
        private String inheritNamespaceTagName;
        private String regex;
        private String xml;
        private String includeTokens;
        private String group;
        private String groupDelimiter;
        private String skipFirst;

        /**
         * The (start) token to use as tokenizer, for example you can use the new line token. You can use simple
         * language as the token to support dynamic tokens.
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * The end token to use as tokenizer if using start/end token pairs. You can use simple language as the token to
         * support dynamic tokens.
         */
        public Builder endToken(String endToken) {
            this.endToken = endToken;
            return this;
        }

        /**
         * To inherit namespaces from a root/parent tag name when using XML You can use simple language as the tag name
         * to support dynamic names.
         */
        public Builder inheritNamespaceTagName(String inheritNamespaceTagName) {
            this.inheritNamespaceTagName = inheritNamespaceTagName;
            return this;
        }

        /**
         * If the token is a regular expression pattern.
         * <p/>
         * The default value is false
         */
        public Builder regex(String regex) {
            this.regex = regex;
            return this;
        }

        /**
         * If the token is a regular expression pattern.
         * <p/>
         * The default value is false
         */
        public Builder regex(boolean regex) {
            this.regex = Boolean.toString(regex);
            return this;
        }

        /**
         * Whether the input is XML messages. This option must be set to true if working with XML payloads.
         */
        public Builder xml(String xml) {
            this.xml = xml;
            return this;
        }

        /**
         * Whether the input is XML messages. This option must be set to true if working with XML payloads.
         */
        public Builder xml(boolean xml) {
            this.xml = Boolean.toString(xml);
            return this;
        }

        /**
         * Whether to include the tokens in the parts when using pairs
         * <p/>
         * The default value is false
         */
        public Builder includeTokens(String includeTokens) {
            this.includeTokens = includeTokens;
            return this;
        }

        /**
         * Whether to include the tokens in the parts when using pairs
         * <p/>
         * The default value is false
         */
        public Builder includeTokens(boolean includeTokens) {
            this.includeTokens = Boolean.toString(includeTokens);
            return this;
        }

        /**
         * To group N parts together, for example to split big files into chunks of 1000 lines. You can use simple
         * language as the group to support dynamic group sizes.
         */
        public Builder group(String group) {
            this.group = group;
            return this;
        }

        /**
         * To group N parts together, for example to split big files into chunks of 1000 lines. You can use simple
         * language as the group to support dynamic group sizes.
         */
        public Builder group(int group) {
            this.group = Integer.toString(group);
            return this;
        }

        /**
         * Sets the delimiter to use when grouping. If this has not been set then token will be used as the delimiter.
         */
        public Builder groupDelimiter(String groupDelimiter) {
            this.groupDelimiter = groupDelimiter;
            return this;
        }

        /**
         * To skip the very first element
         */
        public Builder skipFirst(String skipFirst) {
            this.skipFirst = skipFirst;
            return this;
        }

        /**
         * To skip the very first element
         */
        public Builder skipFirst(boolean skipFirst) {
            this.skipFirst = Boolean.toString(skipFirst);
            return this;
        }

        @Override
        public TokenizerExpression end() {
            return new TokenizerExpression(this);
        }
    }
}
