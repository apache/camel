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

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;

/**
 * Represents a <a href="http://camel.apache.org/hl7.html">HL7</a> {@link org.apache.camel.spi.DataFormat}.
 *
 * @version 
 */
@XmlRootElement(name = "hl7")
@XmlAccessorType(XmlAccessType.FIELD)
public class HL7DataFormat extends DataFormatDefinition {

    @XmlAttribute(required = false)
    private Boolean validate;

    public HL7DataFormat() {
        super("hl7");
    }

    public boolean isValidate() {
        // defaults to true if not configured
        return validate != null ? validate : true;
    }

    public Boolean getValidate() {
        return validate;
    }

    public void setValidate(Boolean validate) {
        this.validate = validate;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        setProperty(dataFormat, "validate", isValidate());
    }



}