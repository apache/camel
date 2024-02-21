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
package org.apache.camel.model.cloud;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

@Metadata(label = "routing,cloud,service-discovery")
@XmlRootElement(name = "dnsServiceDiscovery")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
@Deprecated
public class DnsServiceCallServiceDiscoveryConfiguration extends ServiceCallServiceDiscoveryConfiguration {
    @XmlAttribute
    @Metadata(defaultValue = "_tcp")
    private String proto = "_tcp";
    @XmlAttribute
    private String domain;

    public DnsServiceCallServiceDiscoveryConfiguration() {
        this(null);
    }

    public DnsServiceCallServiceDiscoveryConfiguration(ServiceCallDefinition parent) {
        super(parent, "dns-service-discovery");
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getProto() {
        return proto;
    }

    /**
     * The transport protocol of the desired service.
     */
    public void setProto(String proto) {
        this.proto = proto;
    }

    public String getDomain() {
        return domain;
    }

    /**
     * The domain name;
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    // *************************************************************************
    // Fluent API
    // *************************************************************************

    /**
     * The transport protocol of the desired service.
     */
    public DnsServiceCallServiceDiscoveryConfiguration proto(String proto) {
        setProto(proto);
        return this;
    }

    /**
     * The domain name;
     */
    public DnsServiceCallServiceDiscoveryConfiguration domain(String domain) {
        setDomain(domain);
        return this;
    }
}
