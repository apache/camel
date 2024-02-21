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
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Marshall and unmarshall FHIR objects to/from JSON.
 */
@Metadata(firstVersion = "2.21.0", label = "dataformat,transformation,hl7,json", title = "FHIR JSon")
@XmlRootElement(name = "fhirJson")
@XmlAccessorType(XmlAccessType.FIELD)
public class FhirJsonDataFormat extends FhirDataformat {

    public FhirJsonDataFormat() {
        super("fhirJson");
    }

    private FhirJsonDataFormat(Builder builder) {
        super("fhirJson", builder);
    }

    /**
     * {@code Builder} is a specific builder for {@link FhirJsonDataFormat}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, FhirJsonDataFormat> {

        @Override
        public FhirJsonDataFormat end() {
            return new FhirJsonDataFormat(this);
        }
    }
}
