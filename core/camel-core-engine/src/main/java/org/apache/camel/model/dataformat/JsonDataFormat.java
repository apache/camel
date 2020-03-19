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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * JSon data format is used for unmarshal a JSon payload to POJO or to marshal
 * POJO back to JSon payload.
 */
@Metadata(label = "dataformat,transformation,json", title = "JSon")
@XmlRootElement(name = "json")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String objectMapper;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String useDefaultObjectMapper;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String prettyPrint;
    @XmlAttribute
    @Metadata(defaultValue = "Jackson")
    private JsonLibrary library = JsonLibrary.Jackson;
    @XmlAttribute
    private String unmarshalTypeName;
    @XmlTransient
    private Class<?> unmarshalType;
    @XmlAttribute
    private Class<?> jsonView;
    @XmlAttribute
    private String include;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowJmsType;
    @XmlAttribute
    private String collectionTypeName;
    @XmlTransient
    private Class<?> collectionType;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String useList;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String enableJaxbAnnotationModule;
    @XmlAttribute
    private String moduleClassNames;
    @XmlAttribute
    private String moduleRefs;
    @XmlAttribute
    private String enableFeatures;
    @XmlAttribute
    private String disableFeatures;
    @XmlAttribute
    private String permissions;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowUnmarshallType;
    @XmlAttribute
    private String timezone;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false")
    private String autoDiscoverObjectMapper;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false")
    private String dropRootNode;

    public JsonDataFormat() {
        super("json");
    }

    public JsonDataFormat(JsonLibrary library) {
        this.library = library;
    }

    public String getObjectMapper() {
        return objectMapper;
    }

    /**
     * Lookup and use the existing ObjectMapper with the given id when using
     * Jackson.
     */
    public void setObjectMapper(String objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    /**
     * Whether to lookup and use default Jackson ObjectMapper from the registry.
     */
    public void setUseDefaultObjectMapper(String useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * To enable pretty printing output nicely formatted.
     * <p/>
     * Is by default false.
     */
    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    /**
     * Class name of the java type to use when unmarshalling
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class of the java type to use when unmarshalling
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public JsonLibrary getLibrary() {
        return library;
    }

    /**
     * Which json library to use.
     */
    public void setLibrary(JsonLibrary library) {
        this.library = library;
    }

    public Class<?> getJsonView() {
        return jsonView;
    }

    /**
     * When marshalling a POJO to JSON you might want to exclude certain fields
     * from the JSON output. With Jackson you can use JSON views to accomplish
     * this. This option is to refer to the class which has @JsonView
     * annotations
     */
    public void setJsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
    }

    public String getInclude() {
        return include;
    }

    /**
     * If you want to marshal a pojo to JSON, and the pojo has some fields with
     * null values. And you want to skip these null values, you can set this
     * option to <tt>NON_NULL</tt>
     */
    public void setInclude(String include) {
        this.include = include;
    }

    public String getAllowJmsType() {
        return allowJmsType;
    }

    /**
     * Used for JMS users to allow the JMSType header from the JMS spec to
     * specify a FQN classname to use to unmarshal to.
     */
    public void setAllowJmsType(String allowJmsType) {
        this.allowJmsType = allowJmsType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    /**
     * Refers to a custom collection type to lookup in the registry to use. This
     * option should rarely be used, but allows to use different collection
     * types than java.util.Collection based as default.
     */
    public void setCollectionTypeName(String collectionTypeName) {
        this.collectionTypeName = collectionTypeName;
    }

    public Class<?> getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(Class<?> collectionType) {
        this.collectionType = collectionType;
    }

    public String getUseList() {
        return useList;
    }

    /**
     * To unmarshal to a List of Map or a List of Pojo.
     */
    public void setUseList(String useList) {
        this.useList = useList;
    }

    public String getEnableJaxbAnnotationModule() {
        return enableJaxbAnnotationModule;
    }

    /**
     * Whether to enable the JAXB annotations module when using jackson. When
     * enabled then JAXB annotations can be used by Jackson.
     */
    public void setEnableJaxbAnnotationModule(String enableJaxbAnnotationModule) {
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
    }

    public String getModuleClassNames() {
        return moduleClassNames;
    }

    /**
     * To use custom Jackson modules com.fasterxml.jackson.databind.Module
     * specified as a String with FQN class names. Multiple classes can be
     * separated by comma.
     */
    public void setModuleClassNames(String moduleClassNames) {
        this.moduleClassNames = moduleClassNames;
    }

    public String getModuleRefs() {
        return moduleRefs;
    }

    /**
     * To use custom Jackson modules referred from the Camel registry. Multiple
     * modules can be separated by comma.
     */
    public void setModuleRefs(String moduleRefs) {
        this.moduleRefs = moduleRefs;
    }

    public String getEnableFeatures() {
        return enableFeatures;
    }

    /**
     * Set of features to enable on the Jackson
     * <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
     * <p/>
     * The features should be a name that matches a enum from
     * <tt>com.fasterxml.jackson.databind.SerializationFeature</tt>,
     * <tt>com.fasterxml.jackson.databind.DeserializationFeature</tt>, or
     * <tt>com.fasterxml.jackson.databind.MapperFeature</tt>
     * <p/>
     * Multiple features can be separated by comma
     */
    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    /**
     * Set of features to disable on the Jackson
     * <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
     * <p/>
     * The features should be a name that matches a enum from
     * <tt>com.fasterxml.jackson.databind.SerializationFeature</tt>,
     * <tt>com.fasterxml.jackson.databind.DeserializationFeature</tt>, or
     * <tt>com.fasterxml.jackson.databind.MapperFeature</tt>
     * <p/>
     * Multiple features can be separated by comma
     */
    public void setDisableFeatures(String disableFeatures) {
        this.disableFeatures = disableFeatures;
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
     * allow <tt>com.foo</tt> and all subpackages then specfy
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

    public String getAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled then Jackson is allowed to attempt to use the
     * CamelJacksonUnmarshalType header during the unmarshalling.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setAllowUnmarshallType(String allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
    }

    public String getTimezone() {
        return timezone;
    }

    /**
     * If set then Jackson will use the Timezone when marshalling/unmarshalling.
     * This option will have no effect on the others Json DataFormat, like gson,
     * fastjson and xstream.
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getAutoDiscoverObjectMapper() {
        return autoDiscoverObjectMapper;
    }

    /**
     * If set to true then Jackson will lookup for an objectMapper into the
     * registry
     */
    public void setAutoDiscoverObjectMapper(String autoDiscoverObjectMapper) {
        this.autoDiscoverObjectMapper = autoDiscoverObjectMapper;
    }

    public String getDropRootNode() {
        return dropRootNode;
    }

    /**
     * Whether XStream will drop the root node in the generated JSon.
     * You may want to enable this when using POJOs; as then the written object will include the class name
     * as root node, which is often not intended to be written in the JSon output.
     */
    public void setDropRootNode(String dropRootNode) {
        this.dropRootNode = dropRootNode;
    }

    @Override
    public String getDataFormatName() {
        // json data format is special as the name can be from different bundles
        return "json-" + library.name().toLowerCase();
    }

    //
    // Fluent builders
    //

    public JsonDataFormat objectMapper(String objectMapper) {
        this.objectMapper = objectMapper;
        return this;
    }

    public JsonDataFormat useDefaultObjectMapper(boolean useDefaultObjectMapper) {
        return useDefaultObjectMapper(Boolean.toString(useDefaultObjectMapper));
    }

    public JsonDataFormat useDefaultObjectMapper(String useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
        return this;
    }

    public JsonDataFormat prettyPrint(boolean prettyPrint) {
        return prettyPrint(Boolean.toString(prettyPrint));
    }

    public JsonDataFormat prettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }

    public JsonDataFormat library(JsonLibrary library) {
        this.library = library;
        return this;
    }

    public JsonDataFormat unmarshalType(String unmarshalType) {
        this.unmarshalTypeName = unmarshalType;
        return this;
    }

    public JsonDataFormat unmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
        return this;
    }

    public JsonDataFormat jsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
        return this;
    }

    public JsonDataFormat include(String include) {
        this.include = include;
        return this;
    }

    public JsonDataFormat allowJmsType(boolean allowJmsType) {
        return allowJmsType(Boolean.toString(allowJmsType));
    }

    public JsonDataFormat allowJmsType(String allowJmsType) {
        this.allowJmsType = allowJmsType;
        return this;
    }

    public JsonDataFormat collectionType(String collectionType) {
        this.collectionTypeName = collectionType;
        return this;
    }

    public JsonDataFormat collectionType(Class<?> collectionType) {
        this.collectionType = collectionType;
        return this;
    }

    public JsonDataFormat useList(boolean useList) {
        return useList(Boolean.toString(useList));
    }

    public JsonDataFormat useList(String useList) {
        this.useList = useList;
        return this;
    }

    public JsonDataFormat enableJaxbAnnotationModule(boolean enableJaxbAnnotationModule) {
        return enableJaxbAnnotationModule(Boolean.toString(enableJaxbAnnotationModule));
    }

    public JsonDataFormat enableJaxbAnnotationModule(String enableJaxbAnnotationModule) {
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
        return this;
    }

    public JsonDataFormat moduleClassNames(String moduleClassNames) {
        this.moduleClassNames = moduleClassNames;
        return this;
    }

    public JsonDataFormat moduleRefs(String moduleRefs) {
        this.moduleRefs = moduleRefs;
        return this;
    }

    public JsonDataFormat enableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
        return this;
    }

    public JsonDataFormat disableFeatures(String disableFeatures) {
        this.disableFeatures = disableFeatures;
        return this;
    }

    public JsonDataFormat permissions(String permissions) {
        this.permissions = permissions;
        return this;
    }

    public JsonDataFormat allowUnmarshallType(boolean allowUnmarshallType) {
        return allowUnmarshallType(Boolean.toString(allowUnmarshallType));
    }

    public JsonDataFormat allowUnmarshallType(String allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
        return this;
    }

    public JsonDataFormat timezone(String timezone) {
        this.timezone = timezone;
        return this;
    }

    public JsonDataFormat autoDiscoverObjectMapper(boolean autoDiscoverObjectMapper) {
        return autoDiscoverObjectMapper(Boolean.toString(autoDiscoverObjectMapper));
    }

    public JsonDataFormat autoDiscoverObjectMapper(String autoDiscoverObjectMapper) {
        this.autoDiscoverObjectMapper = autoDiscoverObjectMapper;
        return this;
    }

    public JsonDataFormat dropRootNode(boolean dropRootNode) {
        return dropRootNode(Boolean.toString(dropRootNode));
    }

    public JsonDataFormat dropRootNode(String dropRootNode) {
        this.dropRootNode = dropRootNode;
        return this;
    }

}
