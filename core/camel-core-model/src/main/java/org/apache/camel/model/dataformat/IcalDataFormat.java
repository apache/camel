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
 * Marshal and unmarshal iCal (*.ics) documents to/from model objects.
 */
@Metadata(firstVersion = "2.12.0", label = "dataformat,transformation", title = "iCal")
@XmlRootElement(name = "ical")
@XmlAccessorType(XmlAccessType.FIELD)
public class IcalDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String validating;

    public IcalDataFormat() {
        super("ical");
    }

    private IcalDataFormat(Builder builder) {
        this();
        this.validating = builder.validating;
    }

    public String getValidating() {
        return validating;
    }

    /**
     * Whether to validate.
     */
    public void setValidating(String validating) {
        this.validating = validating;
    }

    /**
     * {@code Builder} is a specific builder for {@link IcalDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<IcalDataFormat> {

        private String validating;

        /**
         * Whether to validate.
         */
        public Builder validating(String validating) {
            this.validating = validating;
            return this;
        }

        /**
         * Whether to validate.
         */
        public Builder validating(boolean validating) {
            this.validating = Boolean.toString(validating);
            return this;
        }

        @Override
        public IcalDataFormat end() {
            return new IcalDataFormat(this);
        }
    }
}
