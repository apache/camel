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

/**
 * CBOR data format is used for unmarshal a CBOR payload to POJO or to marshal
 * POJO back to CBOR payload.
 */
@Metadata(firstVersion = "3.0.0", label = "dataformat,transformation,json", title = "CBOR")
@XmlRootElement(name = "cbor")
@XmlAccessorType(XmlAccessType.FIELD)
public class CBORDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String objectMapper;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String useDefaultObjectMapper;
    @XmlAttribute
    private String unmarshalTypeName;
    @XmlTransient
    private Class<?> unmarshalType;
    @XmlAttribute
    private String collectionTypeName;
    @XmlTransient
    private Class<?> collectionType;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String useList;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String allowUnmarshallType;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String prettyPrint;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String allowJmsType;
    @XmlAttribute
    private String enableFeatures;
    @XmlAttribute
    private String disableFeatures;

    public CBORDataFormat() {
        super("cbor");
    }

    public String getObjectMapper() {
        return objectMapper;
    }

    /**
     * Lookup and use the existing CBOR ObjectMapper with the given id when
     * using Jackson.
     */
    public void setObjectMapper(String objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    /**
     * Whether to lookup and use default Jackson CBOR ObjectMapper from the
     * registry.
     */
    public void setUseDefaultObjectMapper(String useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
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

    /**
     * Class of the java type to use when unmarshalling
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
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

    public String getAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled then Jackson CBOR is allowed to attempt to use the
     * CamelCBORUnmarshalType header during the unmarshalling.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setAllowUnmarshallType(String allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
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

}
