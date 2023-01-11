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
 * Marshal and unmarshal HL7 (Health Care) model objects using the HL7 MLLP codec.
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation,hl7", title = "HL7")
@XmlRootElement(name = "hl7")
@XmlAccessorType(XmlAccessType.FIELD)
public class HL7DataFormat extends DataFormatDefinition {

    @XmlTransient
    @Metadata(label = "advanced", javaType = "ca.uhn.hl7v2.parser.Parser")
    private Object parser;

    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String validate;

    public HL7DataFormat() {
        super("hl7");
    }

    private HL7DataFormat(Builder builder) {
        this();
        this.parser = builder.parser;
        this.validate = builder.validate;
    }

    public String getValidate() {
        return validate;
    }

    /**
     * Whether to validate the HL7 message
     * <p/>
     * Is by default true.
     */
    public void setValidate(String validate) {
        this.validate = validate;
    }

    public Object getParser() {
        return parser;
    }

    /**
     * To use a custom HL7 parser
     */
    public void setParser(Object parser) {
        this.parser = parser;
    }

    /**
     * {@code Builder} is a specific builder for {@link HL7DataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<HL7DataFormat> {

        private Object parser;
        private String validate;

        /**
         * Whether to validate the HL7 message
         * <p/>
         * Is by default true.
         */
        public Builder validate(String validate) {
            this.validate = validate;
            return this;
        }

        /**
         * Whether to validate the HL7 message
         * <p/>
         * Is by default true.
         */
        public Builder validate(boolean validate) {
            this.validate = Boolean.toString(validate);
            return this;
        }

        /**
         * To use a custom HL7 parser
         */
        public Builder parser(Object parser) {
            this.parser = parser;
            return this;
        }

        @Override
        public HL7DataFormat end() {
            return new HL7DataFormat(this);
        }
    }
}
