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
 * Marshal and unmarshal HL7 (Health Care) model objects using the HL7 MLLP codec.
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation,hl7", title = "HL7")
@XmlRootElement(name = "hl7")
@XmlAccessorType(XmlAccessType.FIELD)
public class HL7DataFormat extends DataFormatDefinition {
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String validate;
    @XmlTransient
    private Object parser;

    public HL7DataFormat() {
        super("hl7");
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

}
