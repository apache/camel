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
package org.apache.camel.component.dns;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform DNS queries using DNSJava.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = "dns", title = "DNS", syntax = "dns:dnsType", producerOnly = true,
             category = { Category.NETWORKING }, headersClass = DnsConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not used: the name to query is in a header, CamelDnsDomain for ip,"
                                                + " CamelDnsName for lookup and dig, and CamelDnsTerm for wikipedia"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "dnsType", value = "ip", carriers = Carrier.POJO,
                                                          javaType = "java.net.InetAddress"),
                                           @BodyType.When(option = "dnsType", value = "lookup", carriers = Carrier.LIST,
                                                          javaType = "org.xbill.DNS.Record[]"),
                                           @BodyType.When(option = "dnsType", value = "dig", carriers = Carrier.POJO,
                                                          javaType = "org.xbill.DNS.Message"),
                                           @BodyType.When(option = "dnsType", value = "wikipedia",
                                                          carriers = { Carrier.TEXT, Carrier.NULL }) },
                                   description = "By the type: for ip the InetAddress of the domain, which converts to the"
                                                 + " address as text; for lookup the records of the answer as an array of"
                                                 + " Record, and the lookup fails when there are none; for dig the response"
                                                 + " Message; for wikipedia the text of the first TXT record, or no body"))
public class DnsEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private DnsType dnsType;

    public DnsEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    @SuppressWarnings("deprecation")
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

    public DnsType getDnsType() {
        return dnsType;
    }

    /**
     * The type of the lookup. The wikipedia type is deprecated, as the Wikipedia over DNS service it queries is no
     * longer available.
     */
    public void setDnsType(DnsType dnsType) {
        this.dnsType = dnsType;
    }
}
