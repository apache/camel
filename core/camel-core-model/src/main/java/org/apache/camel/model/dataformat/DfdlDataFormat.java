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
 * Handle DFDL (Data Format Description Language) transformation.
 */
@Metadata(firstVersion = "4.11.0", label = "dataformat,transformation", title = "DFDL")
@XmlRootElement(name = "dfdl")
@XmlAccessorType(XmlAccessType.FIELD)
public class DfdlDataFormat extends DataFormatDefinition {

    // Format options
    @XmlAttribute
    @Metadata(required = true, description = "The path to the DFDL schema file.")
    private String schemaUri;

    @XmlAttribute
    @Metadata(description = "The root element name of the schema to use. If not specified, the first root element in the schema will be used.",
              label = "advanced", defaultValue = "")
    private String rootElement = "";

    @XmlAttribute
    @Metadata(description = "The root namespace of the schema to use.", label = "advanced", defaultValue = "")
    private String rootNamespace = "";

    public DfdlDataFormat() {
        super("dfdl");
    }

    protected DfdlDataFormat(DfdlDataFormat source) {
        super(source);
        this.schemaUri = source.schemaUri;
        this.rootElement = source.rootElement;
        this.rootNamespace = source.rootNamespace;
    }

    public DfdlDataFormat(String schemaUri) {
        this();
        setSchemaUri(schemaUri);
    }

    private DfdlDataFormat(Builder builder) {
        this();
        this.schemaUri = builder.schemaUri;
        this.rootElement = builder.rootElement;
        this.rootNamespace = builder.rootNamespace;
    }

    @Override
    public DfdlDataFormat copyDefinition() {
        return new DfdlDataFormat(this);
    }

    public String getSchemaUri() {
        return schemaUri;
    }

    public void setSchemaUri(String schemaUri) {
        this.schemaUri = schemaUri;
    }

    public String getRootElement() {
        return rootElement;
    }

    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }

    public String getRootNamespace() {
        return rootNamespace;
    }

    public void setRootNamespace(String rootNamespace) {
        this.rootNamespace = rootNamespace;
    }

    /**
     * {@code Builder} is a specific builder for {@link DfdlDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<DfdlDataFormat> {
        private String schemaUri;
        private String rootElement;
        private String rootNamespace;

        /**
         * Sets the DFDL schema URI.
         *
         * @param  schemaUri DFDL schema URI
         * @return           this builder
         */
        public Builder schemaUri(String schemaUri) {
            this.schemaUri = schemaUri;
            return this;
        }

        public Builder rootElement(String rootElement) {
            this.rootElement = rootElement;
            return this;
        }

        public Builder rootNamespace(String rootNamespace) {
            this.rootNamespace = rootNamespace;
            return this;
        }

        @Override
        public DfdlDataFormat end() {
            return new DfdlDataFormat(this);
        }
    }
}
