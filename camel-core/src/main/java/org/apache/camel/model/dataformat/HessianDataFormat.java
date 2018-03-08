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
import org.apache.camel.spi.Metadata;

/**
 * Hessian data format is used for marshalling and unmarshalling messages using Caucho’s Hessian format.
 */
@Metadata(firstVersion = "2.17.0", label = "dataformat,transformation", title = "Hessian")
@XmlRootElement(name = "hessian")
@XmlAccessorType(XmlAccessType.FIELD)
@Deprecated
public class HessianDataFormat extends DataFormatDefinition {
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private Boolean whitelistEnabled = true;
    @XmlAttribute
    private String allowedUnmarshallObjects;
    @XmlAttribute
    private String deniedUnmarshallObjects;
    
    public HessianDataFormat() {
        super("hessian");
    }

    public Boolean getWhitelistEnabled() {
        return whitelistEnabled;
    }

    /**
    * Define if Whitelist feature is enabled or not
    */
    public void setWhitelistEnabled(Boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
    }

    public String getAllowedUnmarshallObjects() {
        return allowedUnmarshallObjects;
    }

    /**
    * Define the allowed objects to be unmarshalled
    */
    public void setAllowedUnmarshallObjects(String allowedUnmarshallObjects) {
        this.allowedUnmarshallObjects = allowedUnmarshallObjects;
    }

    public String getDeniedUnmarshallObjects() {
        return deniedUnmarshallObjects;
    }

    /**
    * Define the denied objects to be unmarshalled
    */
    public void setDeniedUnmarshallObjects(String deniedUnmarshallObjects) {
        this.deniedUnmarshallObjects = deniedUnmarshallObjects;
    }
}
