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
package org.apache.camel.core.xml;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;

/**
 * <code>PropertyPlaceholderDefinition</code> represents a &lt;propertyPlaceholder/&gt element.
 *
 * @version 
 */
@XmlRootElement(name = "propertyPlaceholder")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelPropertyPlaceholderDefinition extends IdentifiedType {

    @XmlAttribute(required = true)
    private String location;

    @XmlAttribute
    private String encoding;

    @XmlAttribute
    private Boolean cache;

    @XmlAttribute
    private Boolean ignoreMissingLocation;

    @XmlAttribute
    private String propertiesResolverRef;

    @XmlAttribute
    private String propertiesParserRef;
    
    @XmlAttribute
    private String propertyPrefix;
    
    @XmlAttribute
    private String propertySuffix;
    
    @XmlAttribute
    private Boolean fallbackToUnaugmentedProperty;
    
    @XmlAttribute
    private String prefixToken;
    
    @XmlAttribute
    private String suffixToken;

    @XmlElement(name = "propertiesFunction")
    private List<CamelPropertyPlaceholderFunctionDefinition> functions;

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Boolean isCache() {
        return cache;
    }

    public void setCache(Boolean cache) {
        this.cache = cache;
    }

    public String getPropertiesResolverRef() {
        return propertiesResolverRef;
    }

    public void setPropertiesResolverRef(String propertiesResolverRef) {
        this.propertiesResolverRef = propertiesResolverRef;
    }

    public String getPropertiesParserRef() {
        return propertiesParserRef;
    }

    public void setPropertiesParserRef(String propertiesParserRef) {
        this.propertiesParserRef = propertiesParserRef;
    }

    public String getPropertyPrefix() {
        return propertyPrefix;
    }

    public void setPropertyPrefix(String propertyPrefix) {
        this.propertyPrefix = propertyPrefix;
    }

    public String getPropertySuffix() {
        return propertySuffix;
    }

    public void setPropertySuffix(String propertySuffix) {
        this.propertySuffix = propertySuffix;
    }

    public Boolean isFallbackToUnaugmentedProperty() {
        return fallbackToUnaugmentedProperty;
    }

    public void setFallbackToUnaugmentedProperty(Boolean fallbackToUnaugmentedProperty) {
        this.fallbackToUnaugmentedProperty = fallbackToUnaugmentedProperty;
    }

    public Boolean isIgnoreMissingLocation() {
        return ignoreMissingLocation;
    }

    public void setIgnoreMissingLocation(Boolean ignoreMissingLocation) {
        this.ignoreMissingLocation = ignoreMissingLocation;
    }

    public String getPrefixToken() {
        return prefixToken;
    }

    public void setPrefixToken(String prefixToken) {
        this.prefixToken = prefixToken;
    }

    public String getSuffixToken() {
        return suffixToken;
    }

    public void setSuffixToken(String suffixToken) {
        this.suffixToken = suffixToken;
    }

    public List<CamelPropertyPlaceholderFunctionDefinition> getFunctions() {
        return functions;
    }

    public void setFunctions(List<CamelPropertyPlaceholderFunctionDefinition> functions) {
        this.functions = functions;
    }
}
