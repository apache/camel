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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Marshal POJOs to JSON and back.
 */
@Metadata(label = "dataformat,transformation,json", title = "JSon")
@XmlRootElement(name = "json")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonDataFormat extends DataFormatDefinition implements ContentTypeHeaderAware {
    @XmlAttribute
    private String objectMapper;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String useDefaultObjectMapper;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false")
    private String autoDiscoverObjectMapper;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String prettyPrint;
    @XmlAttribute
    @Metadata(defaultValue = "Jackson")
    private JsonLibrary library = JsonLibrary.Jackson;
    @XmlAttribute(name = "unmarshalType")
    private String unmarshalTypeName;
    @XmlTransient
    private Class<?> unmarshalType;
    @XmlAttribute(name = "jsonView")
    @Metadata(label = "advanced")
    private String jsonViewTypeName;
    @XmlTransient
    private Class<?> jsonView;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String include;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String allowJmsType;
    @XmlAttribute(name = "collectionType")
    @Metadata(label = "advanced")
    private String collectionTypeName;
    @XmlTransient
    private Class<?> collectionType;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String useList;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String moduleClassNames;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String moduleRefs;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String enableFeatures;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String disableFeatures;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowUnmarshallType;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String timezone;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String schemaResolver;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "true")
    private String autoDiscoverSchemaResolver;
    @XmlAttribute
    @Metadata(description = "If set then Jackson will use the the defined Property Naming Strategy."
                            + "Possible values are: LOWER_CAMEL_CASE, LOWER_DOT_CASE, LOWER_CASE, KEBAB_CASE, SNAKE_CASE and UPPER_CAMEL_CASE")
    private String namingStrategy;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true",
              description = "Whether the data format should set the Content-Type header with the type from the data format."
                            + " For example application/xml for data formats marshalling to XML, or application/json for data formats marshalling to JSON")
    private String contentTypeHeader;
    @XmlAttribute
    @Metadata(description = "To configure the date format while marshall or unmarshall Date fields in JSON using Gson")
    private String dateFormatPattern;

    public JsonDataFormat() {
        super("json");
    }

    public JsonDataFormat(JsonLibrary library) {
        this();
        this.library = library;
    }

    private JsonDataFormat(Builder builder) {
        this();
        this.objectMapper = builder.objectMapper;
        this.useDefaultObjectMapper = builder.useDefaultObjectMapper;
        this.autoDiscoverObjectMapper = builder.autoDiscoverObjectMapper;
        this.prettyPrint = builder.prettyPrint;
        this.library = builder.library;
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.unmarshalType = builder.unmarshalType;
        this.jsonViewTypeName = builder.jsonViewTypeName;
        this.jsonView = builder.jsonView;
        this.include = builder.include;
        this.allowJmsType = builder.allowJmsType;
        this.collectionTypeName = builder.collectionTypeName;
        this.collectionType = builder.collectionType;
        this.useList = builder.useList;
        this.moduleClassNames = builder.moduleClassNames;
        this.moduleRefs = builder.moduleRefs;
        this.enableFeatures = builder.enableFeatures;
        this.disableFeatures = builder.disableFeatures;
        this.allowUnmarshallType = builder.allowUnmarshallType;
        this.timezone = builder.timezone;
        this.schemaResolver = builder.schemaResolver;
        this.autoDiscoverSchemaResolver = builder.autoDiscoverSchemaResolver;
        this.namingStrategy = builder.namingStrategy;
        this.contentTypeHeader = builder.contentTypeHeader;
        this.dateFormatPattern = builder.dateFormatPattern;
    }

    @Override
    public String getDataFormatName() {
        // json data format is special as the name can be from different bundles
        return library.getDataFormatName();
    }

    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public String getDateFormatPattern() {
        return dateFormatPattern;
    }

    public void setDateFormatPattern(String dateFormatPattern) {
        this.dateFormatPattern = dateFormatPattern;
    }

    public String getObjectMapper() {
        return objectMapper;
    }

    /**
     * Lookup and use the existing ObjectMapper with the given id when using Jackson.
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

    public String getJsonViewTypeName() {
        return jsonViewTypeName;
    }

    /**
     * When marshalling a POJO to JSON you might want to exclude certain fields from the JSON output. With Jackson you
     * can use JSON views to accomplish this. This option is to refer to the class which has @JsonView annotations
     */
    public void setJsonViewTypeName(String jsonViewTypeName) {
        this.jsonViewTypeName = jsonViewTypeName;
    }

    public Class<?> getJsonView() {
        return jsonView;
    }

    /**
     * When marshalling a POJO to JSON you might want to exclude certain fields from the JSON output. With Jackson you
     * can use JSON views to accomplish this. This option is to refer to the class which has @JsonView annotations
     */
    public void setJsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
    }

    public String getInclude() {
        return include;
    }

    /**
     * If you want to marshal a pojo to JSON, and the pojo has some fields with null values. And you want to skip these
     * null values, you can set this option to <tt>NON_NULL</tt>
     */
    public void setInclude(String include) {
        this.include = include;
    }

    public String getAllowJmsType() {
        return allowJmsType;
    }

    /**
     * Used for JMS users to allow the JMSType header from the JMS spec to specify a FQN classname to use to unmarshal
     * to.
     */
    public void setAllowJmsType(String allowJmsType) {
        this.allowJmsType = allowJmsType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    /**
     * Refers to a custom collection type to lookup in the registry to use. This option should rarely be used, but
     * allows using different collection types than java.util.Collection based as default.
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

    public String getModuleClassNames() {
        return moduleClassNames;
    }

    /**
     * To use custom Jackson modules com.fasterxml.jackson.databind.Module specified as a String with FQN class names.
     * Multiple classes can be separated by comma.
     */
    public void setModuleClassNames(String moduleClassNames) {
        this.moduleClassNames = moduleClassNames;
    }

    public String getModuleRefs() {
        return moduleRefs;
    }

    /**
     * To use custom Jackson modules referred from the Camel registry. Multiple modules can be separated by comma.
     */
    public void setModuleRefs(String moduleRefs) {
        this.moduleRefs = moduleRefs;
    }

    public String getEnableFeatures() {
        return enableFeatures;
    }

    /**
     * Set of features to enable on the Jackson <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
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
     * Set of features to disable on the Jackson <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
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

    public String getAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled then Jackson is allowed to attempt to use the CamelJacksonUnmarshalType header during the
     * unmarshalling.
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
     * If set then Jackson will use the Timezone when marshalling/unmarshalling. This option will have no effect on the
     * others Json DataFormat, like gson and fastjson.
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getAutoDiscoverObjectMapper() {
        return autoDiscoverObjectMapper;
    }

    /**
     * If set to true then Jackson will look for an objectMapper to use from the registry
     */
    public void setAutoDiscoverObjectMapper(String autoDiscoverObjectMapper) {
        this.autoDiscoverObjectMapper = autoDiscoverObjectMapper;
    }

    /**
     * Optional schema resolver used to lookup schemas for the data in transit.
     */
    public void setSchemaResolver(String schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    public String getSchemaResolver() {
        return schemaResolver;
    }

    public String getAutoDiscoverSchemaResolver() {
        return autoDiscoverSchemaResolver;
    }

    /**
     * When not disabled, the SchemaResolver will be looked up into the registry
     */
    public void setAutoDiscoverSchemaResolver(String autoDiscoverSchemaResolver) {
        this.autoDiscoverSchemaResolver = autoDiscoverSchemaResolver;
    }

    public String getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * If set then Jackson will use the the defined Property Naming Strategy. Possible values are: LOWER_CAMEL_CASE,
     * LOWER_DOT_CASE, LOWER_CASE, KEBAB_CASE, SNAKE_CASE and UPPER_CAMEL_CASE
     */
    public void setNamingStrategy(String namingStrategy) {
        this.namingStrategy = namingStrategy;
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

    public JsonDataFormat namingStrategy(String namingStrategy) {
        this.namingStrategy = namingStrategy;
        return this;
    }

    /**
     * {@code Builder} is a specific builder for {@link JsonDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<JsonDataFormat> {

        private String objectMapper;
        private String useDefaultObjectMapper;
        private String autoDiscoverObjectMapper;
        private String prettyPrint;
        private JsonLibrary library = JsonLibrary.Jackson;
        private String unmarshalTypeName;
        private Class<?> unmarshalType;
        private String jsonViewTypeName;
        private Class<?> jsonView;
        private String include;
        private String allowJmsType;
        private String collectionTypeName;
        private Class<?> collectionType;
        private String useList;
        private String moduleClassNames;
        private String moduleRefs;
        private String enableFeatures;
        private String disableFeatures;
        private String allowUnmarshallType;
        private String timezone;
        private String schemaResolver;
        private String autoDiscoverSchemaResolver;
        private String namingStrategy;
        private String contentTypeHeader;
        private String dateFormatPattern;

        /**
         * Whether the data format should set the Content-Type header with the type from the data format. For example
         * application/xml for data formats marshalling to XML, or application/json for data formats marshalling to JSON
         */
        public Builder contentTypeHeader(String contentTypeHeader) {
            this.contentTypeHeader = contentTypeHeader;
            return this;
        }

        /**
         * Whether the data format should set the Content-Type header with the type from the data format. For example
         * application/xml for data formats marshalling to XML, or application/json for data formats marshalling to JSON
         */
        public Builder contentTypeHeader(boolean contentTypeHeader) {
            this.contentTypeHeader = Boolean.toString(contentTypeHeader);
            return this;
        }

        /**
         * To configure the date format while marshall or unmarshall Date fields in JSON using Gson.
         */
        public Builder dateFormatPattern(String dateFormatPattern) {
            this.dateFormatPattern = dateFormatPattern;
            return this;
        }

        /**
         * Lookup and use the existing ObjectMapper with the given id when using Jackson.
         */
        public Builder objectMapper(String objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * Whether to lookup and use default Jackson ObjectMapper from the registry.
         */
        public Builder useDefaultObjectMapper(String useDefaultObjectMapper) {
            this.useDefaultObjectMapper = useDefaultObjectMapper;
            return this;
        }

        /**
         * Whether to lookup and use default Jackson ObjectMapper from the registry.
         */
        public Builder useDefaultObjectMapper(boolean useDefaultObjectMapper) {
            this.useDefaultObjectMapper = Boolean.toString(useDefaultObjectMapper);
            return this;
        }

        /**
         * To enable pretty printing output nicely formatted.
         * <p/>
         * Is by default false.
         */
        public Builder prettyPrint(String prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * To enable pretty printing output nicely formatted.
         * <p/>
         * Is by default false.
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = Boolean.toString(prettyPrint);
            return this;
        }

        /**
         * Class name of the java type to use when unmarshalling
         */
        public Builder unmarshalTypeName(String unmarshalTypeName) {
            this.unmarshalTypeName = unmarshalTypeName;
            return this;
        }

        /**
         * Class of the java type to use when unmarshalling
         */
        public Builder unmarshalType(Class<?> unmarshalType) {
            this.unmarshalType = unmarshalType;
            return this;
        }

        /**
         * Which json library to use.
         */
        public Builder library(JsonLibrary library) {
            this.library = library;
            return this;
        }

        /**
         * When marshalling a POJO to JSON you might want to exclude certain fields from the JSON output. With Jackson
         * you can use JSON views to accomplish this. This option is to refer to the class which has @JsonView
         * annotations
         */
        public Builder jsonViewTypeName(String jsonViewTypeName) {
            this.jsonViewTypeName = jsonViewTypeName;
            return this;
        }

        /**
         * When marshalling a POJO to JSON you might want to exclude certain fields from the JSON output. With Jackson
         * you can use JSON views to accomplish this. This option is to refer to the class which has @JsonView
         * annotations
         */
        public Builder jsonView(Class<?> jsonView) {
            this.jsonView = jsonView;
            return this;
        }

        /**
         * If you want to marshal a pojo to JSON, and the pojo has some fields with null values. And you want to skip
         * these null values, you can set this option to <tt>NON_NULL</tt>
         */
        public Builder include(String include) {
            this.include = include;
            return this;
        }

        /**
         * Used for JMS users to allow the JMSType header from the JMS spec to specify a FQN classname to use to
         * unmarshal to.
         */
        public Builder allowJmsType(String allowJmsType) {
            this.allowJmsType = allowJmsType;
            return this;
        }

        /**
         * Used for JMS users to allow the JMSType header from the JMS spec to specify a FQN classname to use to
         * unmarshal to.
         */
        public Builder allowJmsType(boolean allowJmsType) {
            this.allowJmsType = Boolean.toString(allowJmsType);
            return this;
        }

        /**
         * Refers to a custom collection type to lookup in the registry to use. This option should rarely be used, but
         * allows using different collection types than java.util.Collection based as default.
         */
        public Builder collectionTypeName(String collectionTypeName) {
            this.collectionTypeName = collectionTypeName;
            return this;
        }

        public Builder collectionType(Class<?> collectionType) {
            this.collectionType = collectionType;
            return this;
        }

        /**
         * To unmarshal to a List of Map or a List of Pojo.
         */
        public Builder useList(String useList) {
            this.useList = useList;
            return this;
        }

        /**
         * To unmarshal to a List of Map or a List of Pojo.
         */
        public Builder useList(boolean useList) {
            this.useList = Boolean.toString(useList);
            return this;
        }

        /**
         * To use custom Jackson modules com.fasterxml.jackson.databind.Module specified as a String with FQN class
         * names. Multiple classes can be separated by comma.
         */
        public Builder moduleClassNames(String moduleClassNames) {
            this.moduleClassNames = moduleClassNames;
            return this;
        }

        /**
         * To use custom Jackson modules referred from the Camel registry. Multiple modules can be separated by comma.
         */
        public Builder moduleRefs(String moduleRefs) {
            this.moduleRefs = moduleRefs;
            return this;
        }

        /**
         * Set of features to enable on the Jackson <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
         * <p/>
         * The features should be a name that matches a enum from
         * <tt>com.fasterxml.jackson.databind.SerializationFeature</tt>,
         * <tt>com.fasterxml.jackson.databind.DeserializationFeature</tt>, or
         * <tt>com.fasterxml.jackson.databind.MapperFeature</tt>
         * <p/>
         * Multiple features can be separated by comma
         */
        public Builder enableFeatures(String enableFeatures) {
            this.enableFeatures = enableFeatures;
            return this;
        }

        /**
         * Set of features to disable on the Jackson <tt>com.fasterxml.jackson.databind.ObjectMapper</tt>.
         * <p/>
         * The features should be a name that matches a enum from
         * <tt>com.fasterxml.jackson.databind.SerializationFeature</tt>,
         * <tt>com.fasterxml.jackson.databind.DeserializationFeature</tt>, or
         * <tt>com.fasterxml.jackson.databind.MapperFeature</tt>
         * <p/>
         * Multiple features can be separated by comma
         */
        public Builder disableFeatures(String disableFeatures) {
            this.disableFeatures = disableFeatures;
            return this;
        }

        /**
         * If enabled then Jackson is allowed to attempt to use the CamelJacksonUnmarshalType header during the
         * unmarshalling.
         * <p/>
         * This should only be enabled when desired to be used.
         */
        public Builder allowUnmarshallType(String allowUnmarshallType) {
            this.allowUnmarshallType = allowUnmarshallType;
            return this;
        }

        /**
         * If enabled then Jackson is allowed to attempt to use the CamelJacksonUnmarshalType header during the
         * unmarshalling.
         * <p/>
         * This should only be enabled when desired to be used.
         */
        public Builder allowUnmarshallType(boolean allowUnmarshallType) {
            this.allowUnmarshallType = Boolean.toString(allowUnmarshallType);
            return this;
        }

        /**
         * If set then Jackson will use the Timezone when marshalling/unmarshalling. This option will have no effect on
         * the others Json DataFormat, like gson and fastjson.
         */
        public Builder timezone(String timezone) {
            this.timezone = timezone;
            return this;
        }

        /**
         * If set to true then Jackson will look for an objectMapper to use from the registry
         */
        public Builder autoDiscoverObjectMapper(String autoDiscoverObjectMapper) {
            this.autoDiscoverObjectMapper = autoDiscoverObjectMapper;
            return this;
        }

        /**
         * If set to true then Jackson will look for an objectMapper to use from the registry
         */
        public Builder autoDiscoverObjectMapper(boolean autoDiscoverObjectMapper) {
            this.autoDiscoverObjectMapper = Boolean.toString(autoDiscoverObjectMapper);
            return this;
        }

        /**
         * Optional schema resolver used to lookup schemas for the data in transit.
         */
        public Builder schemaResolver(String schemaResolver) {
            this.schemaResolver = schemaResolver;
            return this;
        }

        /**
         * When not disabled, the SchemaResolver will be looked up into the registry
         */
        public Builder autoDiscoverSchemaResolver(String autoDiscoverSchemaResolver) {
            this.autoDiscoverSchemaResolver = autoDiscoverSchemaResolver;
            return this;
        }

        /**
         * When not disabled, the SchemaResolver will be looked up into the registry
         */
        public Builder autoDiscoverSchemaResolver(boolean autoDiscoverSchemaResolver) {
            this.autoDiscoverSchemaResolver = Boolean.toString(autoDiscoverSchemaResolver);
            return this;
        }

        /**
         * If set then Jackson will use the the defined Property Naming Strategy. Possible values are: LOWER_CAMEL_CASE,
         * LOWER_DOT_CASE, LOWER_CASE, KEBAB_CASE, SNAKE_CASE and UPPER_CAMEL_CASE
         */
        public Builder namingStrategy(String namingStrategy) {
            this.namingStrategy = namingStrategy;
            return this;
        }

        @Override
        public JsonDataFormat end() {
            return new JsonDataFormat(this);
        }
    }
}
