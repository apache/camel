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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * XStream data format is used for unmarshal a XML payload to POJO or to marshal
 * POJO back to XML payload.
 */
@Metadata(firstVersion = "1.3.0", label = "dataformat,transformation,xml,json", title = "XStream")
@XmlRootElement(name = "xstream")
@XmlAccessorType(XmlAccessType.NONE)
public class XStreamDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String permissions;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    private String driver;
    @XmlAttribute
    private String driverRef;
    @XmlAttribute
    private String mode;

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

    @Override
    public String getDataFormatName() {
        return "json".equals(driver) ? "json-xstream" : "xstream";
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
     * To use a custom XStream driver. The instance must be of type
     * com.thoughtworks.xstream.io.HierarchicalStreamDriver
     */
    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getDriverRef() {
        return driverRef;
    }

    /**
     * To refer to a custom XStream driver to lookup in the registry. The
     * instance must be of type
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
     * List of class names for using custom XStream converters. The classes must
     * be of type com.thoughtworks.xstream.converters.Converter
     */
    public void setConverters(List<PropertyDefinition> converters) {
        this.converters = converters;
    }

    public void setConverters(Map<String, String> converters) {
        this.converters = new ArrayList<>();
        converters.forEach((k, v) -> this.converters.add(new PropertyDefinition(k, v)));
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
        this.aliases = new ArrayList<>();
        aliases.forEach((k, v) -> this.aliases.add(new PropertyDefinition(k, v)));
    }

    public List<PropertyDefinition> getOmitFields() {
        return omitFields;
    }

    /**
     * Prevents a field from being serialized. To omit a field you must always
     * provide the declaring type and not necessarily the type that is
     * converted. Multiple values can be separated by comma.
     */
    public void setOmitFields(List<PropertyDefinition> omitFields) {
        this.omitFields = omitFields;
    }

    public void setOmitFields(Map<String, String> aliases) {
        this.omitFields = new ArrayList<>();
        aliases.forEach((k, v) -> this.omitFields.add(new PropertyDefinition(k, v)));
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
     * Adds a default implicit collection which is used for any unmapped XML tag.
     * Multiple values can be separated by comma.
     */
    public void setImplicitCollections(List<PropertyDefinition> implicitCollections) {
        this.implicitCollections = implicitCollections;
    }

    public void setImplicitCollections(Map<String, String> implicitCollections) {
        this.implicitCollections = new ArrayList<>();
        implicitCollections.forEach((k, v) -> this.implicitCollections.add(new PropertyDefinition(k, v)));
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
     * Adds permissions that controls which Java packages and classes XStream is
     * allowed to use during unmarshal from xml/json to Java beans.
     * <p/>
     * A permission must be configured either here or globally using a JVM
     * system property. The permission can be specified in a syntax where a plus
     * sign is allow, and minus sign is deny. <br/>
     * Wildcards is supported by using <tt>.*</tt> as prefix. For example to
     * allow <tt>com.foo</tt> and all subpackages then specify
     * <tt>+com.foo.*</tt>. Multiple permissions can be configured separated by
     * comma, such as <tt>+com.foo.*,-com.foo.bar.MySecretBean</tt>. <br/>
     * The following default permission is always included:
     * <tt>"-*,java.lang.*,java.util.*"</tt> unless its overridden by specifying
     * a JVM system property with they key
     * <tt>org.apache.camel.xstream.permissions</tt>.
     */
    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    /**
     * To add permission for the given pojo classes.
     * 
     * @param type the pojo class(es) xstream should use as allowed permission
     * @see #setPermissions(String)
     */
    public void setPermissions(Class<?>... type) {
        CollectionStringBuffer csb = new CollectionStringBuffer(",");
        for (Class<?> clazz : type) {
            csb.append("+");
            csb.append(clazz.getName());
        }
        setPermissions(csb.toString());
    }

}
