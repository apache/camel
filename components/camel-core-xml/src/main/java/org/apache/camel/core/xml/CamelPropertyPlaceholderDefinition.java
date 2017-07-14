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
import org.apache.camel.spi.Metadata;

/**
 * Properties placeholder
 *
 * @version 
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "propertyPlaceholder")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelPropertyPlaceholderDefinition extends IdentifiedType {

    @XmlAttribute
    private String location;
    @XmlAttribute
    private String encoding;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean cache;
    @XmlAttribute @Metadata(defaultValue = "false")
    private Boolean ignoreMissingLocation;
    @XmlAttribute
    private String propertiesResolverRef;
    @XmlAttribute
    private String propertiesParserRef;
    @XmlAttribute
    private String propertyPrefix;
    @XmlAttribute
    private String propertySuffix;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean fallbackToUnaugmentedProperty;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean defaultFallbackEnabled;
    @XmlAttribute @Metadata(defaultValue = "{{")
    private String prefixToken;
    @XmlAttribute @Metadata(defaultValue = "}}")
    private String suffixToken;
    @XmlElement(name = "propertiesFunction")
    private List<CamelPropertyPlaceholderFunctionDefinition> functions;
    @XmlElement(name = "propertiesLocation")
    private List<CamelPropertyPlaceholderLocationDefinition> locations;

    public String getLocation() {
        return location;
    }

    /**
     * A list of locations to load properties. You can use comma to separate multiple locations.
     * This option will override any default locations and only use the locations from this option.
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Encoding to use when loading properties file from the file system or classpath.
     * <p/>
     * If no encoding has been set, then the properties files is loaded using ISO-8859-1 encoding (latin-1)
     * as documented by {@link java.util.Properties#load(java.io.InputStream)}
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Boolean isCache() {
        return cache;
    }

    /**
     * Whether or not to cache loaded properties. The default value is true.
     */
    public void setCache(Boolean cache) {
        this.cache = cache;
    }

    public String getPropertiesResolverRef() {
        return propertiesResolverRef;
    }

    /**
     * Reference to a custom PropertiesResolver to be used
     */
    public void setPropertiesResolverRef(String propertiesResolverRef) {
        this.propertiesResolverRef = propertiesResolverRef;
    }

    public String getPropertiesParserRef() {
        return propertiesParserRef;
    }

    /**
     * Reference to a custom PropertiesParser to be used
     */
    public void setPropertiesParserRef(String propertiesParserRef) {
        this.propertiesParserRef = propertiesParserRef;
    }

    public String getPropertyPrefix() {
        return propertyPrefix;
    }

    /**
     * Optional prefix prepended to property names before resolution.
     */
    public void setPropertyPrefix(String propertyPrefix) {
        this.propertyPrefix = propertyPrefix;
    }

    public String getPropertySuffix() {
        return propertySuffix;
    }

    /**
     * Optional suffix appended to property names before resolution.
     */
    public void setPropertySuffix(String propertySuffix) {
        this.propertySuffix = propertySuffix;
    }

    public Boolean isFallbackToUnaugmentedProperty() {
        return fallbackToUnaugmentedProperty;
    }

    /**
     * If true, first attempt resolution of property name augmented with propertyPrefix and propertySuffix
     * before falling back the plain property name specified. If false, only the augmented property name is searched.
     */
    public void setFallbackToUnaugmentedProperty(Boolean fallbackToUnaugmentedProperty) {
        this.fallbackToUnaugmentedProperty = fallbackToUnaugmentedProperty;
    }

    public Boolean getDefaultFallbackEnabled() {
        return defaultFallbackEnabled;
    }

    /**
     * If false, the component does not attempt to find a default for the key by looking after the colon separator.
     */
    public void setDefaultFallbackEnabled(Boolean defaultFallbackEnabled) {
        this.defaultFallbackEnabled = defaultFallbackEnabled;
    }

    public Boolean isIgnoreMissingLocation() {
        return ignoreMissingLocation;
    }

    /**
     * Whether to silently ignore if a location cannot be located, such as a properties file not found.
     */
    public void setIgnoreMissingLocation(Boolean ignoreMissingLocation) {
        this.ignoreMissingLocation = ignoreMissingLocation;
    }

    public String getPrefixToken() {
        return prefixToken;
    }

    /**
     * Sets the value of the prefix token used to identify properties to replace.  Setting a value of
     * {@code null} restores the default token {{
     */
    public void setPrefixToken(String prefixToken) {
        this.prefixToken = prefixToken;
    }

    public String getSuffixToken() {
        return suffixToken;
    }

    /**
     * Sets the value of the suffix token used to identify properties to replace.  Setting a value of
     * {@code null} restores the default token }}
     */
    public void setSuffixToken(String suffixToken) {
        this.suffixToken = suffixToken;
    }

    public List<CamelPropertyPlaceholderFunctionDefinition> getFunctions() {
        return functions;
    }

    /**
     * List of custom properties function to use.
     */
    public void setFunctions(List<CamelPropertyPlaceholderFunctionDefinition> functions) {
        this.functions = functions;
    }

    public List<CamelPropertyPlaceholderLocationDefinition> getLocations() {
        return locations;
    }

    /**
     * List of property locations to use.
     */
    public void setLocations(List<CamelPropertyPlaceholderLocationDefinition> locations) {
        this.locations = locations;
    }
}
