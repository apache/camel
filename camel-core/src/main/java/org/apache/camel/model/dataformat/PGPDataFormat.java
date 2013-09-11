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

/**
 * Represents a <a href="http://camel.apache.org/crypto.html">pgp</a>
 * {@link org.apache.camel.spi.DataFormat}.
 */
@XmlRootElement(name = "pgp")
@XmlAccessorType(XmlAccessType.FIELD)
public class PGPDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String keyUserid;
    @XmlAttribute
    private String password;
    @XmlAttribute
    private String keyFileName;
    @XmlAttribute
    private Boolean armored;
    @XmlAttribute
    private Boolean integrity;

    public PGPDataFormat() {
        super("pgp");
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (keyUserid != null) {
            setProperty(camelContext, dataFormat, "keyUserid", keyUserid);
        }
        if (password != null) {
            setProperty(camelContext, dataFormat, "password", password);
        }
        if (keyFileName != null) {
            setProperty(camelContext, dataFormat, "keyFileName", keyFileName);
        }
        if (armored != null) {
            setProperty(camelContext, dataFormat, "armored", armored);
        }
        if (integrity != null) {
            setProperty(camelContext, dataFormat, "integrity", integrity);
        }
    }

    public Boolean getArmored() {
        return armored;
    }

    public void setArmored(Boolean armored) {
        this.armored = armored;
    }

    public Boolean getIntegrity() {
        return integrity;
    }

    public void setIntegrity(Boolean integrity) {
        this.integrity = integrity;
    }

    public String getKeyFileName() {
        return keyFileName;
    }

    public void setKeyFileName(String keyFileName) {
        this.keyFileName = keyFileName;
    }

    public String getKeyUserid() {
        return keyUserid;
    }

    public void setKeyUserid(String keyUserid) {
        this.keyUserid = keyUserid;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}