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
 * Unmarshal an XML payloads to POJOs and back using XMLMapper extension of Jackson.
 */
@Metadata(firstVersion = "2.16.0", label = "dataformat,transformation,xml", title = "Jackson XML",
          description = "Unmarshal an XML payloads to POJOs and back using XMLMapper extension of Jackson")
@XmlRootElement(name = "jacksonXml")
@XmlAccessorType(XmlAccessType.FIELD)
public class JacksonXMLDataFormat extends DataFormatDefinition implements ContentTypeHeaderAware {

    @XmlTransient
    private Class<?> unmarshalType;
    @XmlTransient
    private Class<?> jsonView;
    @XmlTransient
    private Class<?> collectionType;

    @XmlAttribute
    @Metadata(label = "advanced", description = "Lookup and use the existing XmlMapper with the given id.")
    private String xmlMapper;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether to enable pretty printing output nicely formatted. Is by default false.")
    private String prettyPrint;
    @XmlAttribute(name = "unmarshalType")
    @Metadata(description = "Class name of the java type to use when unmarshalling.")
    private String unmarshalTypeName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether to allow Jackson to use the CamelJacksonUnmarshalType header during unmarshalling. Should only be enabled when desired.")
    private String allowUnmarshallType;
    @XmlAttribute(name = "jsonView")
    @Metadata(description = "When marshalling a POJO to JSON you might want to exclude certain fields from the JSON output."
                            + " With Jackson you can use JSON views to accomplish this. This option is to refer to the class which has @JsonView annotations.")
    private String jsonViewTypeName;
    @XmlAttribute
    @Metadata(description = "If you want to marshal a POJO to JSON, and the POJO has some fields with null values."
                            + " And you want to skip these null values, you can set this option to NON_NULL.")
    private String include;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Whether to allow the JMSType header from the JMS spec to specify a FQN classname to use to unmarshal to.")
    private String allowJmsType;
    @XmlAttribute(name = "collectionType")
    @Metadata(label = "advanced",
              description = "Refers to a custom collection type to lookup in the registry to use."
                            + " This option should rarely be used, but allows using different collection types than java.util.Collection based as default.")
    private String collectionTypeName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether to unmarshal to a List of Map or a List of Pojo.")
    private String useList;
    @XmlAttribute
    @Metadata(label = "advanced", description = "If set then Jackson will use the Timezone when marshalling/unmarshalling.")
    private String timezone;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Whether to enable the JAXB annotations module when using Jackson. When enabled then JAXB annotations can be used by Jackson.")
    private String enableJaxbAnnotationModule;
    @XmlAttribute
    @Metadata(label = "advanced",
              description = "To use custom Jackson modules com.fasterxml.jackson.databind.Module specified as a String with FQN class names."
                            + " Multiple classes can be separated by comma.")
    private String moduleClassNames;
    @XmlAttribute
    @Metadata(label = "advanced",
              description = "To use custom Jackson modules referred from the Camel registry. Multiple modules can be separated by comma.")
    private String moduleRefs;
    @XmlAttribute
    @Metadata(description = "Set of features to enable on the Jackson com.fasterxml.jackson.databind.ObjectMapper."
                            + " The features should be a name that matches a enum from SerializationFeature, DeserializationFeature, or MapperFeature."
                            + " Multiple features can be separated by comma.")
    private String enableFeatures;
    @XmlAttribute
    @Metadata(description = "Set of features to disable on the Jackson com.fasterxml.jackson.databind.ObjectMapper."
                            + " The features should be a name that matches a enum from SerializationFeature, DeserializationFeature, or MapperFeature."
                            + " Multiple features can be separated by comma.")
    private String disableFeatures;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true",
              description = "Whether the data format should set the Content-Type header with the type from the data format."
                            + " For example application/xml for data formats marshalling to XML, or application/json for data formats marshalling to JSON")
    private String contentTypeHeader;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Integer",
              description = "Maximum allowed string length when deserializing (in chars or bytes, depending on input context). The default is 20,000,000.")
    private String maxStringLength;

    public JacksonXMLDataFormat() {
        super("jacksonXml");
    }

    protected JacksonXMLDataFormat(JacksonXMLDataFormat builder) {
        super(builder);
        this.unmarshalType = builder.unmarshalType;
        this.jsonView = builder.jsonView;
        this.collectionType = builder.collectionType;
        this.xmlMapper = builder.xmlMapper;
        this.prettyPrint = builder.prettyPrint;
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.allowUnmarshallType = builder.allowUnmarshallType;
        this.jsonViewTypeName = builder.jsonViewTypeName;
        this.include = builder.include;
        this.allowJmsType = builder.allowJmsType;
        this.collectionTypeName = builder.collectionTypeName;
        this.useList = builder.useList;
        this.timezone = builder.timezone;
        this.enableJaxbAnnotationModule = builder.enableJaxbAnnotationModule;
        this.moduleClassNames = builder.moduleClassNames;
        this.moduleRefs = builder.moduleRefs;
        this.enableFeatures = builder.enableFeatures;
        this.disableFeatures = builder.disableFeatures;
        this.contentTypeHeader = builder.contentTypeHeader;
        this.maxStringLength = builder.maxStringLength;
    }

    private JacksonXMLDataFormat(Builder builder) {
        this();
        this.unmarshalType = builder.unmarshalType;
        this.jsonView = builder.jsonView;
        this.collectionType = builder.collectionType;
        this.xmlMapper = builder.xmlMapper;
        this.prettyPrint = builder.prettyPrint;
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.allowUnmarshallType = builder.allowUnmarshallType;
        this.jsonViewTypeName = builder.jsonViewTypeName;
        this.include = builder.include;
        this.allowJmsType = builder.allowJmsType;
        this.collectionTypeName = builder.collectionTypeName;
        this.useList = builder.useList;
        this.timezone = builder.timezone;
        this.enableJaxbAnnotationModule = builder.enableJaxbAnnotationModule;
        this.moduleClassNames = builder.moduleClassNames;
        this.moduleRefs = builder.moduleRefs;
        this.enableFeatures = builder.enableFeatures;
        this.disableFeatures = builder.disableFeatures;
        this.contentTypeHeader = builder.contentTypeHeader;
        this.maxStringLength = builder.maxStringLength;
    }

    @Override
    public JacksonXMLDataFormat copyDefinition() {
        return new JacksonXMLDataFormat(this);
    }

    public String getXmlMapper() {
        return xmlMapper;
    }

    public void setXmlMapper(String xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public String getJsonViewTypeName() {
        return jsonViewTypeName;
    }

    public void setJsonViewTypeName(String jsonViewTypeName) {
        this.jsonViewTypeName = jsonViewTypeName;
    }

    public Class<?> getJsonView() {
        return jsonView;
    }

    public void setJsonView(Class<?> jsonView) {
        this.jsonView = jsonView;
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public String getAllowJmsType() {
        return allowJmsType;
    }

    public void setAllowJmsType(String allowJmsType) {
        this.allowJmsType = allowJmsType;
    }

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

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

    public void setUseList(String useList) {
        this.useList = useList;
    }

    public String getEnableJaxbAnnotationModule() {
        return enableJaxbAnnotationModule;
    }

    public void setEnableJaxbAnnotationModule(String enableJaxbAnnotationModule) {
        this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
    }

    public String getModuleClassNames() {
        return moduleClassNames;
    }

    public void setModuleClassNames(String moduleClassNames) {
        this.moduleClassNames = moduleClassNames;
    }

    public String getModuleRefs() {
        return moduleRefs;
    }

    public void setModuleRefs(String moduleRefs) {
        this.moduleRefs = moduleRefs;
    }

    public String getEnableFeatures() {
        return enableFeatures;
    }

    public void setEnableFeatures(String enableFeatures) {
        this.enableFeatures = enableFeatures;
    }

    public String getDisableFeatures() {
        return disableFeatures;
    }

    public void setDisableFeatures(String disableFeatures) {
        this.disableFeatures = disableFeatures;
    }

    public String getAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    public void setAllowUnmarshallType(String allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
    }

    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    public void setContentTypeHeader(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getMaxStringLength() {
        return maxStringLength;
    }

    public void setMaxStringLength(String maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    /**
     * {@code Builder} is a specific builder for {@link JacksonXMLDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<JacksonXMLDataFormat> {

        private Class<?> unmarshalType;
        private Class<?> jsonView;
        private Class<?> collectionType;
        private String xmlMapper;
        private String prettyPrint;
        private String unmarshalTypeName;
        private String allowUnmarshallType;
        private String jsonViewTypeName;
        private String include;
        private String allowJmsType;
        private String collectionTypeName;
        private String useList;
        private String timezone;
        private String enableJaxbAnnotationModule;
        private String moduleClassNames;
        private String moduleRefs;
        private String enableFeatures;
        private String disableFeatures;
        private String contentTypeHeader;
        private String maxStringLength;

        /**
         * Lookup and use the existing XmlMapper with the given id.
         */
        public Builder xmlMapper(String xmlMapper) {
            this.xmlMapper = xmlMapper;
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
         * allows to use different collection types than java.util.Collection based as default.
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
         * Whether to enable the JAXB annotations module when using jackson. When enabled then JAXB annotations can be
         * used by Jackson.
         */
        public Builder enableJaxbAnnotationModule(String enableJaxbAnnotationModule) {
            this.enableJaxbAnnotationModule = enableJaxbAnnotationModule;
            return this;
        }

        /**
         * Whether to enable the JAXB annotations module when using jackson. When enabled then JAXB annotations can be
         * used by Jackson.
         */
        public Builder enableJaxbAnnotationModule(boolean enableJaxbAnnotationModule) {
            this.enableJaxbAnnotationModule = Boolean.toString(enableJaxbAnnotationModule);
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

        public Builder contentTypeHeader(String contentTypeHeader) {
            this.contentTypeHeader = contentTypeHeader;
            return this;
        }

        public Builder contentTypeHeader(boolean contentTypeHeader) {
            this.contentTypeHeader = Boolean.toString(contentTypeHeader);
            return this;
        }

        /**
         * If set then Jackson will use the Timezone when marshalling/unmarshalling.
         */
        public Builder timezone(String timezone) {
            this.timezone = timezone;
            return this;
        }

        /**
         * Sets the maximum string length (in chars or bytes, depending on input context). The default is 20,000,000.
         * This limit is not exact, the limit is applied when we increase internal buffer sizes and an exception will
         * happen at sizes greater than this limit. Some text values that are a little bigger than the limit may be
         * treated as valid but no text values with sizes less than or equal to this limit will be treated as invalid.
         */
        public Builder maxStringLength(String maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        @Override
        public JacksonXMLDataFormat end() {
            return new JacksonXMLDataFormat(this);
        }
    }
}
