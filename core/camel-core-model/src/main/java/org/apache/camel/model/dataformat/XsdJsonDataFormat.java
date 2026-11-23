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
 * Unmarshal XML to JSON and marshal JSON to XML, driven by an XML Schema (an XSD, or the types of a WSDL), which gives
 * the types, arrays, order and namespaces.
 */
@Metadata(firstVersion = "4.24.0", label = "dataformat,transformation,xml,json", title = "XSD JSON")
@XmlRootElement(name = "xsdJson")
@XmlAccessorType(XmlAccessType.FIELD)
public class XsdJsonDataFormat extends DataFormatDefinition {

    @XmlAttribute(required = true)
    @Metadata(required = true, supportFileReference = true,
              description = "The XML Schema to use: an .xsd file, or a .wsdl file whose types (every xs:schema in wsdl:types)"
                            + " are read. A resource such as classpath:orders.xsd, file:schemas/orders.wsdl or an https URL."
                            + " Imports and includes are resolved relative to it, with the same scheme only.")
    private String schema;
    @XmlAttribute
    @Metadata(description = "The root element: a global element of the schema, as {namespace}name or as a local name that is"
                            + " unique in the schema. Needed to marshal (JSON to XML) unless includeRoot is enabled. On unmarshal"
                            + " the XML document names its root element, and this option only restricts it. The header"
                            + " CamelXsdJsonElement overrides it per message.")
    private String element;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false",
              description = "Whether the JSON holds the root element as its single field, such as {getOrder: {...}}, instead of"
                            + " the content of the root element.")
    private String includeRoot;
    @XmlAttribute
    @Metadata(description = "A prefix for the JSON fields of XML attributes, such as _. By default attributes and child elements"
                            + " share the JSON object without a prefix.")
    private String attributePrefix;
    @XmlAttribute
    @Metadata(defaultValue = "value",
              description = "The JSON field of the text of an element with simple content and attributes, such as"
                            + " {currency: EUR, value: 9.95}.")
    private String textName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false",
              description = "Whether to skip XML elements and attributes, or JSON fields, that the schema does not declare,"
                            + " instead of failing.")
    private String ignoreUnknown;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "false",
              description = "Whether to indent the output, the JSON on unmarshal and the XML on marshal.")
    private String prettyPrint;

    public XsdJsonDataFormat() {
        super("xsdJson");
    }

    public XsdJsonDataFormat(XsdJsonDataFormat source) {
        super(source);
        this.schema = source.schema;
        this.element = source.element;
        this.includeRoot = source.includeRoot;
        this.attributePrefix = source.attributePrefix;
        this.textName = source.textName;
        this.ignoreUnknown = source.ignoreUnknown;
        this.prettyPrint = source.prettyPrint;
    }

    private XsdJsonDataFormat(Builder builder) {
        this();
        this.schema = builder.schema;
        this.element = builder.element;
        this.includeRoot = builder.includeRoot;
        this.attributePrefix = builder.attributePrefix;
        this.textName = builder.textName;
        this.ignoreUnknown = builder.ignoreUnknown;
        this.prettyPrint = builder.prettyPrint;
    }

    @Override
    public XsdJsonDataFormat copyDefinition() {
        return new XsdJsonDataFormat(this);
    }

    public String getSchema() {
        return schema;
    }

    /**
     * The XML Schema to use: an .xsd file, or a .wsdl file whose types are read.
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getElement() {
        return element;
    }

    /**
     * The root element: a global element of the schema, as {namespace}name or as a unique local name.
     */
    public void setElement(String element) {
        this.element = element;
    }

    public String getIncludeRoot() {
        return includeRoot;
    }

    /**
     * Whether the JSON holds the root element as its single field.
     */
    public void setIncludeRoot(String includeRoot) {
        this.includeRoot = includeRoot;
    }

    public String getAttributePrefix() {
        return attributePrefix;
    }

    /**
     * A prefix for the JSON fields of XML attributes.
     */
    public void setAttributePrefix(String attributePrefix) {
        this.attributePrefix = attributePrefix;
    }

    public String getTextName() {
        return textName;
    }

    /**
     * The JSON field of the text of an element with simple content and attributes.
     */
    public void setTextName(String textName) {
        this.textName = textName;
    }

    public String getIgnoreUnknown() {
        return ignoreUnknown;
    }

    /**
     * Whether to skip XML elements and attributes, or JSON fields, that the schema does not declare.
     */
    public void setIgnoreUnknown(String ignoreUnknown) {
        this.ignoreUnknown = ignoreUnknown;
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    /**
     * Whether to indent the output.
     */
    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * {@code Builder} is a specific builder for {@link XsdJsonDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<XsdJsonDataFormat> {

        private String schema;
        private String element;
        private String includeRoot;
        private String attributePrefix;
        private String textName;
        private String ignoreUnknown;
        private String prettyPrint;

        /**
         * The XML Schema to use: an .xsd file, or a .wsdl file whose types are read.
         */
        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        /**
         * The root element: a global element of the schema, as {namespace}name or as a unique local name.
         */
        public Builder element(String element) {
            this.element = element;
            return this;
        }

        /**
         * Whether the JSON holds the root element as its single field.
         */
        public Builder includeRoot(String includeRoot) {
            this.includeRoot = includeRoot;
            return this;
        }

        /**
         * Whether the JSON holds the root element as its single field.
         */
        public Builder includeRoot(boolean includeRoot) {
            this.includeRoot = Boolean.toString(includeRoot);
            return this;
        }

        /**
         * A prefix for the JSON fields of XML attributes.
         */
        public Builder attributePrefix(String attributePrefix) {
            this.attributePrefix = attributePrefix;
            return this;
        }

        /**
         * The JSON field of the text of an element with simple content and attributes.
         */
        public Builder textName(String textName) {
            this.textName = textName;
            return this;
        }

        /**
         * Whether to skip XML elements and attributes, or JSON fields, that the schema does not declare.
         */
        public Builder ignoreUnknown(String ignoreUnknown) {
            this.ignoreUnknown = ignoreUnknown;
            return this;
        }

        /**
         * Whether to skip XML elements and attributes, or JSON fields, that the schema does not declare.
         */
        public Builder ignoreUnknown(boolean ignoreUnknown) {
            this.ignoreUnknown = Boolean.toString(ignoreUnknown);
            return this;
        }

        /**
         * Whether to indent the output.
         */
        public Builder prettyPrint(String prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * Whether to indent the output.
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = Boolean.toString(prettyPrint);
            return this;
        }

        @Override
        public XsdJsonDataFormat end() {
            return new XsdJsonDataFormat(this);
        }
    }
}
