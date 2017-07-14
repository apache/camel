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
package org.apache.camel.component.dns;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * To lookup domain information and run DNS queries using DNSJava.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = "dns", title = "DNS", syntax = "dns:dnsType", producerOnly = true, label = "networking")
public class DnsEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private DnsType dnsType;

    public DnsEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        if (DnsType.dig == dnsType) {
            return new DnsDigProducer(this);
        } else if (DnsType.ip == dnsType) {
            return new DnsIpProducer(this);
        } else if (DnsType.lookup == dnsType) {
            return new DnsLookupProducer(this);
        } else if (DnsType.wikipedia == dnsType) {
            return new DnsWikipediaProducer(this);
        }
        // should not happen
        return null;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    public DnsType getDnsType() {
        return dnsType;
    }

    /**
     * The type of the lookup.
     */
    public void setDnsType(DnsType dnsType) {
        this.dnsType = dnsType;
    }
}
