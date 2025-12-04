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
 * Transform between XML and Groovy Node (Map structure) objects.
 */
@Metadata(firstVersion = "4.15.0", label = "dataformat,transformation,xml", title = "Groovy XML")
@XmlRootElement(name = "groovyXml")
@XmlAccessorType(XmlAccessType.FIELD)
public class GroovyXmlDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String attributeMapping;

    public GroovyXmlDataFormat() {
        super("groovyXml");
    }

    protected GroovyXmlDataFormat(GroovyXmlDataFormat source) {
        super(source);
        this.attributeMapping = source.attributeMapping;
    }

    private GroovyXmlDataFormat(Builder builder) {
        this();
        this.attributeMapping = builder.attributeMapping;
    }

    @Override
    public GroovyXmlDataFormat copyDefinition() {
        return new GroovyXmlDataFormat(this);
    }

    public String getAttributeMapping() {
        return attributeMapping;
    }

    /**
     * To turn on or off attribute mapping. When enabled then keys that start with _ or @ character will be mapped to an
     * XML attribute, and vise versa. This rule is what Jackson and other XML or JSon libraries uses.
     */
    public void setAttributeMapping(String attributeMapping) {
        this.attributeMapping = attributeMapping;
    }

    @Override
    public String toString() {
        return "GroovyXmlDataFormat'";
    }

    @XmlTransient
    public static class Builder implements DataFormatBuilder<GroovyXmlDataFormat> {

        private String attributeMapping;

        /**
         * To turn on or off attribute mapping. When enabled then keys that start with _ or @ character will be mapped
         * to an XML attribute, and vise versa. This rule is what Jackson and other XML or JSon libraries uses.
         */
        public Builder attributeMapping(String attributeMapping) {
            this.attributeMapping = attributeMapping;
            return this;
        }

        /**
         * To turn on or off attribute mapping. When enabled then keys that start with _ or @ character will be mapped
         * to an XML attribute, and vise versa. This rule is what Jackson and other XML or JSon libraries uses.
         */
        public Builder attributeMapping(boolean attributeMapping) {
            this.attributeMapping = Boolean.toString(attributeMapping);
            return this;
        }

        @Override
        public GroovyXmlDataFormat end() {
            return new GroovyXmlDataFormat(this);
        }
    }
}
