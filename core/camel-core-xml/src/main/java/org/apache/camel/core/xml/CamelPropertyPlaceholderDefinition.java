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
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "propertyPlaceholder")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelPropertyPlaceholderDefinition extends IdentifiedType {

    @XmlAttribute
    private String location;
    @XmlAttribute
    private String encoding;
    @XmlAttribute @Metadata(defaultValue = "false")
    private Boolean ignoreMissingLocation;
    @XmlAttribute
    private String propertiesParserRef;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean defaultFallbackEnabled;
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

    public String getPropertiesParserRef() {
        return propertiesParserRef;
    }

    /**
     * Reference to a custom PropertiesParser to be used
     */
    public void setPropertiesParserRef(String propertiesParserRef) {
        this.propertiesParserRef = propertiesParserRef;
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
