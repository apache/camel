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
 * Marshal and unmarshal OCSF (Open Cybersecurity Schema Framework) security events to/from JSON.
 */
@Metadata(firstVersion = "4.18.0", label = "dataformat,transformation,json,security", title = "OCSF")
@XmlRootElement(name = "ocsf")
@XmlAccessorType(XmlAccessType.FIELD)
public class OcsfDataFormat extends DataFormatDefinition {

    @XmlTransient
    private Class<?> collectionType;
    @XmlTransient
    private Class<?> unmarshalType;

    @XmlAttribute
    @Metadata(label = "advanced")
    private String objectMapper;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String useDefaultObjectMapper;
    @XmlAttribute(name = "unmarshalType")
    private String unmarshalTypeName;
    @XmlAttribute(name = "collectionType")
    @Metadata(label = "advanced")
    private String collectionTypeName;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String useList;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String allowUnmarshallType;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String prettyPrint;

    public OcsfDataFormat() {
        super("ocsf");
    }

    protected OcsfDataFormat(OcsfDataFormat source) {
        super(source);
        this.collectionType = source.collectionType;
        this.unmarshalType = source.unmarshalType;
        this.objectMapper = source.objectMapper;
        this.useDefaultObjectMapper = source.useDefaultObjectMapper;
        this.unmarshalTypeName = source.unmarshalTypeName;
        this.collectionTypeName = source.collectionTypeName;
        this.useList = source.useList;
        this.allowUnmarshallType = source.allowUnmarshallType;
        this.prettyPrint = source.prettyPrint;
    }

    public OcsfDataFormat(Class<?> unmarshalType) {
        this();
        this.unmarshalType = unmarshalType;
        if (unmarshalType != null) {
            this.unmarshalTypeName = unmarshalType.getName();
        }
    }

    private OcsfDataFormat(Builder builder) {
        this();
        this.collectionType = builder.collectionType;
        this.unmarshalType = builder.unmarshalType;
        this.objectMapper = builder.objectMapper;
        this.useDefaultObjectMapper = builder.useDefaultObjectMapper;
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.collectionTypeName = builder.collectionTypeName;
        this.useList = builder.useList;
        this.allowUnmarshallType = builder.allowUnmarshallType;
        this.prettyPrint = builder.prettyPrint;
    }

    @Override
    public OcsfDataFormat copyDefinition() {
        return new OcsfDataFormat(this);
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

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    /**
     * Class name of the OCSF event type to use when unmarshalling. Defaults to OcsfEvent.
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class of the OCSF event type to use when unmarshalling.
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
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

    public String getCollectionTypeName() {
        return collectionTypeName;
    }

    /**
     * Refers to a custom collection type to lookup in the registry to use. This option should rarely be used, but
     * allows to use different collection types than java.util.Collection based as default.
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
     * To unmarshal to a List of OCSF events.
     */
    public void setUseList(String useList) {
        this.useList = useList;
    }

    public String getAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled then the unmarshal type can be specified via the CamelOcsfUnmarshalType header.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setAllowUnmarshallType(String allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
    }

    /**
     * {@code Builder} is a specific builder for {@link OcsfDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<OcsfDataFormat> {

        private Class<?> collectionType;
        private Class<?> unmarshalType;
        private String objectMapper;
        private String useDefaultObjectMapper;
        private String unmarshalTypeName;
        private String collectionTypeName;
        private String useList;
        private String allowUnmarshallType;
        private String prettyPrint;

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
         * Class name of the OCSF event type to use when unmarshalling.
         */
        public Builder unmarshalTypeName(String unmarshalTypeName) {
            this.unmarshalTypeName = unmarshalTypeName;
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
         * Class of the OCSF event type to use when unmarshalling.
         */
        public Builder unmarshalType(Class<?> unmarshalType) {
            this.unmarshalType = unmarshalType;
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
         * To unmarshal to a List of OCSF events.
         */
        public Builder useList(String useList) {
            this.useList = useList;
            return this;
        }

        /**
         * To unmarshal to a List of OCSF events.
         */
        public Builder useList(boolean useList) {
            this.useList = Boolean.toString(useList);
            return this;
        }

        /**
         * If enabled then the unmarshal type can be specified via the CamelOcsfUnmarshalType header.
         * <p/>
         * This should only be enabled when desired to be used.
         */
        public Builder allowUnmarshallType(String allowUnmarshallType) {
            this.allowUnmarshallType = allowUnmarshallType;
            return this;
        }

        /**
         * If enabled then the unmarshal type can be specified via the CamelOcsfUnmarshalType header.
         * <p/>
         * This should only be enabled when desired to be used.
         */
        public Builder allowUnmarshallType(boolean allowUnmarshallType) {
            this.allowUnmarshallType = Boolean.toString(allowUnmarshallType);
            return this;
        }

        @Override
        public OcsfDataFormat end() {
            return new OcsfDataFormat(this);
        }
    }
}
