/**
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

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;

/**
 * The FHIR JSon data format is used to marshall/unmarshall to/from FHIR objects to/from JSON.
 */
@Metadata(firstVersion = "2.21.0", label = "dataformat,transformation,hl7", title = "FHIR JSon")
@XmlRootElement(name = "fhirJson")
@XmlAccessorType(XmlAccessType.FIELD)
public class FhirJsonDataFormat extends DataFormatDefinition {

    @XmlTransient @Metadata(label = "advanced")
    private Object fhirContext;

    @XmlAttribute @Metadata(enums = "DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4", defaultValue = "DSTU3")
    private String fhirVersion;

    public FhirJsonDataFormat() {
        super("fhirJson");
    }

    public Object getFhirContext() {
        return fhirContext;
    }

    public void setFhirContext(Object fhirContext) {
        this.fhirContext = fhirContext;
    }

    public String getFhirVersion() {
        return fhirVersion;
    }

    /**
     * The version of FHIR to use. Possible values are: DSTU2,DSTU2_HL7ORG,DSTU2_1,DSTU3,R4
     */
    public void setFhirVersion(String fhirVersion) {
        this.fhirVersion = fhirVersion;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (getContentTypeHeader() != null) {
            setProperty(camelContext, dataFormat, "contentTypeHeader", getContentTypeHeader());
        }
        if (getFhirContext() != null) {
            setProperty(camelContext, dataFormat, "fhirContext", getFhirContext());
        }
        if (getFhirVersion() != null) {
            setProperty(camelContext, dataFormat, "fhirVersion", getFhirVersion());
        }
    }
}
