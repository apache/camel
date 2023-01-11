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
package org.apache.camel.model.dataformat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Marshal and unmarshal POJOs to/from XML using <a href="https://x-stream.github.io/">XStream</a> library.
 */
@Metadata(firstVersion = "1.3.0", label = "dataformat,transformation,xml,json", title = "XStream")
@XmlRootElement(name = "xstream")
@XmlAccessorType(XmlAccessType.NONE)
@Deprecated
public class XStreamDataFormat extends DataFormatDefinition implements ContentTypeHeaderAware {

    @XmlAttribute
    private String permissions;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "com.thoughtworks.xstream.io.HierarchicalStreamDriver")
    private String driver;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "com.thoughtworks.xstream.io.HierarchicalStreamDriver")
    private String driverRef;
    @XmlAttribute
    @Metadata(label = "advanced",
              enums = "NO_REFERENCES,ID_REFERENCES,XPATH_RELATIVE_REFERENCES,XPATH_ABSOLUTE_REFERENCES,SINGLE_NODE_XPATH_RELATIVE_REFERENCES,SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES")
    private String mode;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true",
              description = "Whether the data format should set the Content-Type header with the type from the data format."
                            + " For example application/xml for data formats marshalling to XML, or application/json for data formats marshalling to JSON")
    private String contentTypeHeader;
    @XmlElement(name = "converters")
    private List<PropertyDefinition> converters;
    @XmlElement(name = "aliases")
    private List<PropertyDefinition> aliases;
    @XmlElement(name = "omitFields")
    private List<PropertyDefinition> omitFields;
    @XmlElement(name = "implicitCollections")
    private List<PropertyDefinition> implicitCollections;

    public XStreamDataFormat() {
        super("xstream");
    }

    public XStreamDataFormat(String encoding) {
        this();
        setEncoding(encoding);
    }

    private XStreamDataFormat(Builder builder) {
        this();
        this.permissions = builder.permissions;
        this.encoding = builder.encoding;
        this.driver = builder.driver;
        this.driverRef = builder.driverRef;
        this.mode = builder.mode;
        this.contentTypeHeader = builder.contentTypeHeader;
        this.converters = builder.converters;
        this.aliases = builder.aliases;
        this.omitFields = builder.omitFields;
        this.implicitCollections = builder.implicitCollections;
    }

    @Override
    public String getDataFormatName() {
        return "json".equals(driver) ? "xstreamJson" : "xstream";
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the encoding to use
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getDriver() {
        return driver;
    }

    /**
     * To use a custom XStream driver. The instance must be of type com.thoughtworks.xstream.io.HierarchicalStreamDriver
     */
    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getDriverRef() {
        return driverRef;
    }

    /**
     * To refer to a custom XStream driver to lookup in the registry. The instance must be of type
     * com.thoughtworks.xstream.io.HierarchicalStreamDriver
     */
    public void setDriverRef(String driverRef) {
        this.driverRef = driverRef;
    }

    public String getMode() {
        return mode;
    }

    /**
     * Mode for dealing with duplicate references The possible values are:
     * <ul>
     * <li>NO_REFERENCES</li>
     * <li>ID_REFERENCES</li>
     * <li>XPATH_RELATIVE_REFERENCES</li>
     * <li>XPATH_ABSOLUTE_REFERENCES</li>
     * <li>SINGLE_NODE_XPATH_RELATIVE_REFERENCES</li>
     * <li>SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES</li>
     * </ul>
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<PropertyDefinition> getConverters() {
        return converters;
    }

    public Map<String, String> getConvertersAsMap() {
        if (converters == null || converters.isEmpty()) {
            return null;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        for (PropertyDefinition def : converters) {
            answer.put(def.getKey(), def.getValue());
        }
        return answer;
    }

    /**
     * List of class names for using custom XStream converters. The classes must be of type
     * com.thoughtworks.xstream.converters.Converter
     */
    public void setConverters(List<PropertyDefinition> converters) {
        this.converters = converters;
    }

    public void setConverters(Map<String, String> converters) {
        setConverters(toList(converters));
    }

    private static List<PropertyDefinition> toList(Map<String, String> map) {
        List<PropertyDefinition> result = new ArrayList<>(map.size());
        map.forEach((k, v) -> result.add(new PropertyDefinition(k, v)));
        return result;
    }

    public List<PropertyDefinition> getAliases() {
        return aliases;
    }

    public Map<String, String> getAliasesAsMap() {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        for (PropertyDefinition def : aliases) {
            answer.put(def.getKey(), def.getValue());
        }
        return answer;
    }

    /**
     * Alias a Class to a shorter name to be used in XML elements.
     */
    public void setAliases(List<PropertyDefinition> aliases) {
        this.aliases = aliases;
    }

    public void setAliases(Map<String, String> aliases) {
        setAliases(toList(aliases));
    }

    public List<PropertyDefinition> getOmitFields() {
        return omitFields;
    }

    /**
     * Prevents a field from being serialized. To omit a field you must always provide the declaring type and not
     * necessarily the type that is converted. Multiple values can be separated by comma.
     */
    public void setOmitFields(List<PropertyDefinition> omitFields) {
        this.omitFields = omitFields;
    }

    public void setOmitFields(Map<String, String> aliases) {
        setOmitFields(toList(aliases));
    }

    public Map<String, String> getOmitFieldsAsMap() {
        if (omitFields == null || omitFields.isEmpty()) {
            return null;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        for (PropertyDefinition def : omitFields) {
            answer.put(def.getKey(), def.getValue());
        }
        return answer;
    }

    public List<PropertyDefinition> getImplicitCollections() {
        return implicitCollections;
    }

    /**
     * Adds a default implicit collection which is used for any unmapped XML tag. Multiple values can be separated by
     * comma.
     */
    public void setImplicitCollections(List<PropertyDefinition> implicitCollections) {
        this.implicitCollections = implicitCollections;
    }

    public void setImplicitCollections(Map<String, String> implicitCollections) {
        setImplicitCollections(toList(implicitCollections));
    }

    public Map<String, String> getImplicitCollectionsAsMap() {
        if (implicitCollections == null || implicitCollections.isEmpty()) {
            return null;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        for (PropertyDefinition def : implicitCollections) {
            answer.put(def.getKey(), def.getValue());
        }
        return answer;
    }

    public String getPermissions() {
        return permissions;
    }

    /**
     * Adds permissions that controls which Java packages and classes XStream is allowed to use during unmarshal from
     * xml/json to Java beans.
     * <p/>
     * A permission must be configured either here or globally using a JVM system property. The permission can be
     * specified in a syntax where a plus sign is allow, and minus sign is deny. <br/>
     * Wildcards is supported by using <tt>.*</tt> as prefix. For example to allow <tt>com.foo</tt> and all subpackages
     * then specify <tt>+com.foo.*</tt>. Multiple permissions can be configured separated by comma, such as
     * <tt>+com.foo.*,-com.foo.bar.MySecretBean</tt>. <br/>
     * The following default permission is always included: <tt>"-*,java.lang.*,java.util.*"</tt> unless its overridden
     * by specifying a JVM system property with they key <tt>org.apache.camel.xstream.permissions</tt>.
     */
    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    /**
     * To add permission for the given pojo classes.
     *
     * @param type the pojo class(es) xstream should use as allowed permission
     * @see        #setPermissions(String)
     */
    public void setPermissions(Class<?>... type) {
        setPermissions(toString(type));
    }

    private static String toString(Class<?>[] type) {
        StringJoiner permissionsBuilder = new StringJoiner(",");
        for (Class<?> clazz : type) {
            permissionsBuilder.add("+");
            permissionsBuilder.add(clazz.getName());
        }
        return permissionsBuilder.toString();
    }

    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    /**
     * {@code Builder} is a specific builder for {@link XStreamDataFormat}.
     */
    @XmlTransient
    @Deprecated
    public static class Builder implements DataFormatBuilder<XStreamDataFormat> {

        private String permissions;
        private String encoding;
        private String driver;
        private String driverRef;
        private String mode;
        private String contentTypeHeader;
        private List<PropertyDefinition> converters;
        private List<PropertyDefinition> aliases;
        private List<PropertyDefinition> omitFields;
        private List<PropertyDefinition> implicitCollections;

        /**
         * Sets the encoding to use
         */
        public Builder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /**
         * To use a custom XStream driver. The instance must be of type
         * com.thoughtworks.xstream.io.HierarchicalStreamDriver
         */
        public Builder driver(String driver) {
            this.driver = driver;
            return this;
        }

        /**
         * To refer to a custom XStream driver to lookup in the registry. The instance must be of type
         * com.thoughtworks.xstream.io.HierarchicalStreamDriver
         */
        public Builder driverRef(String driverRef) {
            this.driverRef = driverRef;
            return this;
        }

        /**
         * Mode for dealing with duplicate references The possible values are:
         * <ul>
         * <li>NO_REFERENCES</li>
         * <li>ID_REFERENCES</li>
         * <li>XPATH_RELATIVE_REFERENCES</li>
         * <li>XPATH_ABSOLUTE_REFERENCES</li>
         * <li>SINGLE_NODE_XPATH_RELATIVE_REFERENCES</li>
         * <li>SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES</li>
         * </ul>
         */
        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        /**
         * List of class names for using custom XStream converters. The classes must be of type
         * com.thoughtworks.xstream.converters.Converter
         */
        public Builder converters(List<PropertyDefinition> converters) {
            this.converters = converters;
            return this;
        }

        public Builder converters(Map<String, String> converters) {
            return converters(XStreamDataFormat.toList(converters));
        }

        /**
         * Alias a Class to a shorter name to be used in XML elements.
         */
        public Builder aliases(List<PropertyDefinition> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder aliases(Map<String, String> aliases) {
            return aliases(toList(aliases));
        }

        /**
         * Prevents a field from being serialized. To omit a field you must always provide the declaring type and not
         * necessarily the type that is converted. Multiple values can be separated by comma.
         */
        public Builder omitFields(List<PropertyDefinition> omitFields) {
            this.omitFields = omitFields;
            return this;
        }

        public Builder omitFields(Map<String, String> aliases) {
            return omitFields(XStreamDataFormat.toList(aliases));
        }

        /**
         * Adds a default implicit collection which is used for any unmapped XML tag. Multiple values can be separated
         * by comma.
         */
        public Builder implicitCollections(List<PropertyDefinition> implicitCollections) {
            this.implicitCollections = implicitCollections;
            return this;
        }

        public Builder implicitCollections(Map<String, String> implicitCollections) {
            return implicitCollections(XStreamDataFormat.toList(implicitCollections));
        }

        /**
         * Adds permissions that controls which Java packages and classes XStream is allowed to use during unmarshal
         * from xml/json to Java beans.
         * <p/>
         * A permission must be configured either here or globally using a JVM system property. The permission can be
         * specified in a syntax where a plus sign is allow, and minus sign is deny. <br/>
         * Wildcards is supported by using <tt>.*</tt> as prefix. For example to allow <tt>com.foo</tt> and all
         * subpackages then specify <tt>+com.foo.*</tt>. Multiple permissions can be configured separated by comma, such
         * as <tt>+com.foo.*,-com.foo.bar.MySecretBean</tt>. <br/>
         * The following default permission is always included: <tt>"-*,java.lang.*,java.util.*"</tt> unless its
         * overridden by specifying a JVM system property with they key <tt>org.apache.camel.xstream.permissions</tt>.
         */
        public Builder permissions(String permissions) {
            this.permissions = permissions;
            return this;
        }

        /**
         * To add permission for the given pojo classes.
         *
         * @param type the pojo class(es) xstream should use as allowed permission
         * @see        #setPermissions(String)
         */
        public Builder permissions(Class<?>... type) {
            return permissions(XStreamDataFormat.toString(type));
        }

        public Builder contentTypeHeader(String contentTypeHeader) {
            this.contentTypeHeader = contentTypeHeader;
            return this;
        }

        public Builder contentTypeHeader(boolean contentTypeHeader) {
            this.contentTypeHeader = Boolean.toString(contentTypeHeader);
            return this;
        }

        @Override
        public XStreamDataFormat end() {
            return new XStreamDataFormat(this);
        }
    }
}
