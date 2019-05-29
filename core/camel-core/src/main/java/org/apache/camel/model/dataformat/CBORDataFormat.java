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
@Metadata(firstVersion = "3.0.0",label = "dataformat,transformation,json", title = "CBOR")
@XmlRootElement(name = "cbor")
@XmlAccessorType(XmlAccessType.FIELD)
public class CBORDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String objectMapper;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private Boolean useDefaultObjectMapper;
    @XmlTransient
    private Class<?> unmarshalType;
    @XmlTransient
    private Class<?> collectionType;
    @XmlAttribute
    private Boolean useList;
    @XmlAttribute
    private Boolean allowUnmarshallType;

    public CBORDataFormat() {
        super("cbor");
    }

    public String getObjectMapper() {
        return objectMapper;
    }

    /**
     * Lookup and use the existing CBOR ObjectMapper with the given id when using
     * Jackson.
     */
    public void setObjectMapper(String objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Boolean getUseDefaultObjectMapper() {
        return useDefaultObjectMapper;
    }

    /**
     * Whether to lookup and use default Jackson CBOR ObjectMapper from the registry.
     */
    public void setUseDefaultObjectMapper(Boolean useDefaultObjectMapper) {
        this.useDefaultObjectMapper = useDefaultObjectMapper;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class of the java type to use when unarmshalling
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public Class<?> getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(Class<?> collectionType) {
        this.collectionType = collectionType;
    }

    public Boolean getUseList() {
        return useList;
    }

    /**
     * To unarmshal to a List of Map or a List of Pojo.
     */
    public void setUseList(Boolean useList) {
        this.useList = useList;
    }

    public Boolean getAllowUnmarshallType() {
        return allowUnmarshallType;
    }

    /**
     * If enabled then Jackson is allowed to attempt to use the
     * CamelJacksonUnmarshalType header during the unmarshalling.
     * <p/>
     * This should only be enabled when desired to be used.
     */
    public void setAllowUnmarshallType(Boolean allowUnmarshallType) {
        this.allowUnmarshallType = allowUnmarshallType;
    }

    @Override
    public String getDataFormatName() {
        return "cbor";
    }

}
