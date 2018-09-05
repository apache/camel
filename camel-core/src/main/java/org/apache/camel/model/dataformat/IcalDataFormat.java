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

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;

/**
 * The iCal dataformat is used for working with iCalendar messages.
 */
@Metadata(firstVersion = "2.12.0", label = "dataformat,transformation", title = "iCal")
@XmlRootElement(name = "ical")
@XmlAccessorType(XmlAccessType.FIELD)
public class IcalDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private Boolean validating;

    public IcalDataFormat() {
        super("ical");
    }

    public Boolean getValidating() {
        return validating;
    }

    /**
     * Whether to validate.
     */
    public void setValidating(Boolean validating) {
        this.validating = validating;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (getValidating() != null) {
            setProperty(camelContext, dataFormat, "validating", getValidating());
        }
    }

}