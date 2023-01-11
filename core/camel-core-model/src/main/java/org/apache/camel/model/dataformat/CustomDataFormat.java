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
 * Delegate to a custom {@link org.apache.camel.spi.DataFormat} implementation via Camel registry.
 */
@Metadata(label = "dataformat,transformation", title = "Custom")
@XmlRootElement(name = "custom")
@XmlAccessorType(XmlAccessType.FIELD)
public class CustomDataFormat extends DataFormatDefinition {

    @XmlAttribute(required = true)
    private String ref;

    public CustomDataFormat() {
    }

    public CustomDataFormat(String ref) {
        this.ref = ref;
    }

    private CustomDataFormat(Builder builder) {
        this();
        this.ref = builder.ref;
    }

    /**
     * Reference to the custom {@link org.apache.camel.spi.DataFormat} to lookup from the Camel registry.
     */
    public String getRef() {
        return ref;
    }

    /**
     * Reference to the custom {@link org.apache.camel.spi.DataFormat} to lookup from the Camel registry.
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    @Override
    public String toString() {
        return "CustomDataFormat[" + ref + "]";
    }

    /**
     * {@code Builder} is a specific builder for {@link CustomDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<CustomDataFormat> {

        private String ref;

        /**
         * Reference to the custom {@link org.apache.camel.spi.DataFormat} to lookup from the Camel registry.
         */
        public Builder ref(String ref) {
            this.ref = ref;
            return this;
        }

        @Override
        public CustomDataFormat end() {
            return new CustomDataFormat(this);
        }
    }
}
