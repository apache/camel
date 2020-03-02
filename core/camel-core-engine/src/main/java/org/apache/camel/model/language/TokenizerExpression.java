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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * To use Camel message body or header with a tokenizer in Camel expressions or
 * predicates.
 *
 * @see org.apache.camel.language.tokenizer.TokenizeLanguage
 */
@Metadata(firstVersion = "2.0.0", label = "language,core", title = "Tokenize")
@XmlRootElement(name = "tokenize")
@XmlAccessorType(XmlAccessType.FIELD)
public class TokenizerExpression extends ExpressionDefinition {
    @XmlAttribute(required = true)
    private String token;
    @XmlAttribute
    private String endToken;
    @XmlAttribute
    private String inheritNamespaceTagName;
    @XmlAttribute
    private String headerName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String regex;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String xml;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String includeTokens;
    @XmlAttribute
    private String group;
    @XmlAttribute
    private String groupDelimiter;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String skipFirst;

    public TokenizerExpression() {
    }

    public TokenizerExpression(String token) {
        this.token = token;
    }

    @Override
    public String getLanguage() {
        return "tokenize";
    }

    public String getToken() {
        return token;
    }

    /**
     * The (start) token to use as tokenizer, for example you can use the new
     * line token. You can use simple language as the token to support dynamic
     * tokens.
     */
    public void setToken(String token) {
        this.token = token;
    }

    public String getEndToken() {
        return endToken;
    }

    /**
     * The end token to use as tokenizer if using start/end token pairs. You can
     * use simple language as the token to support dynamic tokens.
     */
    public void setEndToken(String endToken) {
        this.endToken = endToken;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to tokenize instead of using the message body.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
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
     * To inherit namespaces from a root/parent tag name when using XML You can
     * use simple language as the tag name to support dynamic names.
     */
    public void setInheritNamespaceTagName(String inheritNamespaceTagName) {
        this.inheritNamespaceTagName = inheritNamespaceTagName;
    }

    public String getXml() {
        return xml;
    }

    /**
     * Whether the input is XML messages. This option must be set to true if
     * working with XML payloads.
     */
    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getIncludeTokens() {
        return includeTokens;
    }

    /**
     * Whether to include the tokens in the parts when using pairs
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
     * To group N parts together, for example to split big files into chunks of
     * 1000 lines. You can use simple language as the group to support dynamic
     * group sizes.
     */
    public void setGroup(String group) {
        this.group = group;
    }

    public String getGroupDelimiter() {
        return groupDelimiter;
    }

    /**
     * Sets the delimiter to use when grouping. If this has not been set then
     * token will be used as the delimiter.
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

    @Override
    public String toString() {
        if (endToken != null) {
            return "tokenize{body() using tokens: " + token + "..." + endToken + "}";
        } else {
            return "tokenize{" + (headerName != null ? "header: " + headerName : "body()") + " using token: " + token + "}";
        }
    }
}
