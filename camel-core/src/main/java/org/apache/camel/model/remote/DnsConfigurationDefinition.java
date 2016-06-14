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

package org.apache.camel.model.remote;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * DNS remote service call configuration
 */
@Metadata(label = "eip,routing,remote")
@XmlRootElement(name = "dnsConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class DnsConfigurationDefinition extends ServiceCallConfigurationDefinition {

    @XmlAttribute @Metadata(defaultValue = "_tcp")
    String proto = "_tcp";

    @XmlAttribute
    String domain;

    public DnsConfigurationDefinition() {
    }

    public DnsConfigurationDefinition(ServiceCallDefinition parent) {
        super(parent);
    }

    // -------------------------------------------------------------------------
    // Getter/Setter
    // -------------------------------------------------------------------------

    public String getProto() {
        return proto;
    }

    public void setProto(String proto) {
        this.proto = proto;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    // -------------------------------------------------------------------------
    // Fluent API
    // -------------------------------------------------------------------------
 
    /**
     * The transport protocol of the desired service.
     */
    public DnsConfigurationDefinition proto(String proto) {
        setProto(proto);
        return this;
    }

    /**
     * The domain name;
     */
    public DnsConfigurationDefinition domain(String domain) {
        setDomain(domain);
        return this;
    }
}
