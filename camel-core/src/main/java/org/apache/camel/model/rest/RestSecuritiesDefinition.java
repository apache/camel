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
package org.apache.camel.model.rest;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * To configure security definition
 */
@Metadata(label = "rest,security", title = "Security Definitions")
@XmlRootElement(name = "securityDefinitions")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestSecuritiesDefinition {

    @XmlElements({
        @XmlElement(name = "apiKey", type = RestSecurityApiKey.class),
        @XmlElement(name = "basicAuth", type = RestSecurityBasicAuth.class),
        @XmlElement(name = "oauth2", type = RestSecurityOAuth2.class)
    })
    private List<RestSecurityDefinition> securityDefinitions = new ArrayList<>();

    public List<RestSecurityDefinition> getSecurityDefinitions() {
        return securityDefinitions;
    }

    public void setSecurityDefinitions(List<RestSecurityDefinition> securityDefinitions) {
        this.securityDefinitions = securityDefinitions;
    }
}
