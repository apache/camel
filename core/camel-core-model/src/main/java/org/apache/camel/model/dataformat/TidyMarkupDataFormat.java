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

import org.w3c.dom.Node;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Parse (potentially invalid) HTML into valid HTML or DOM.
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation", title = "TidyMarkup")
@XmlRootElement(name = "tidyMarkup")
@XmlAccessorType(XmlAccessType.FIELD)
public class TidyMarkupDataFormat extends DataFormatDefinition {

    @XmlTransient
    private Class<?> dataObjectType;

    @XmlAttribute(name = "dataObjectType")
    @Metadata(defaultValue = "org.w3c.dom.Node", enums = "org.w3c.dom.Node,java.lang.String")
    private String dataObjectTypeName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String omitXmlDeclaration;

    public TidyMarkupDataFormat() {
        super("tidyMarkup");
        this.setDataObjectType(Node.class);
    }

    public TidyMarkupDataFormat(Class<?> dataObjectType) {
        this();
        if (!dataObjectType.isAssignableFrom(String.class) && !dataObjectType.isAssignableFrom(Node.class)) {
            throw new IllegalArgumentException(
                    "TidyMarkupDataFormat only supports returning a String or a org.w3c.dom.Node object");
        }
        this.setDataObjectType(dataObjectType);
    }

    private TidyMarkupDataFormat(Builder builder) {
        this();
        this.dataObjectType = builder.dataObjectType;
        this.dataObjectTypeName = builder.dataObjectTypeName;
        this.omitXmlDeclaration = builder.omitXmlDeclaration;
    }

    /**
     * What data type to unmarshal as, can either be org.w3c.dom.Node or java.lang.String.
     * <p/>
     * Is by default org.w3c.dom.Node
     */
    public void setDataObjectType(Class<?> dataObjectType) {
        this.dataObjectType = dataObjectType;
    }

    public Class<?> getDataObjectType() {
        return dataObjectType;
    }

    public String getDataObjectTypeName() {
        return dataObjectTypeName;
    }

    /**
     * What data type to unmarshal as, can either be org.w3c.dom.Node or java.lang.String.
     * <p/>
     * Is by default org.w3c.dom.Node
     */
    public void setDataObjectTypeName(String dataObjectTypeName) {
        this.dataObjectTypeName = dataObjectTypeName;
    }

    public String getOmitXmlDeclaration() {
        return omitXmlDeclaration;
    }

    /**
     * When returning a String, do we omit the XML declaration in the top.
     */
    public void setOmitXmlDeclaration(String omitXmlDeclaration) {
        this.omitXmlDeclaration = omitXmlDeclaration;
    }

    /**
     * {@code Builder} is a specific builder for {@link TidyMarkupDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<TidyMarkupDataFormat> {

        private Class<?> dataObjectType;
        private String dataObjectTypeName;
        private String omitXmlDeclaration;

        /**
         * What data type to unmarshal as, can either be org.w3c.dom.Node or java.lang.String.
         * <p/>
         * Is by default org.w3c.dom.Node
         */
        public Builder dataObjectType(Class<?> dataObjectType) {
            this.dataObjectType = dataObjectType;
            return this;
        }

        /**
         * What data type to unmarshal as, can either be org.w3c.dom.Node or java.lang.String.
         * <p/>
         * Is by default org.w3c.dom.Node
         */
        public Builder dataObjectTypeName(String dataObjectTypeName) {
            this.dataObjectTypeName = dataObjectTypeName;
            return this;
        }

        /**
         * When returning a String, do we omit the XML declaration in the top.
         */
        public Builder omitXmlDeclaration(String omitXmlDeclaration) {
            this.omitXmlDeclaration = omitXmlDeclaration;
            return this;
        }

        /**
         * When returning a String, do we omit the XML declaration in the top.
         */
        public Builder omitXmlDeclaration(boolean omitXmlDeclaration) {
            this.omitXmlDeclaration = Boolean.toString(omitXmlDeclaration);
            return this;
        }

        @Override
        public TidyMarkupDataFormat end() {
            return new TidyMarkupDataFormat(this);
        }
    }
}
