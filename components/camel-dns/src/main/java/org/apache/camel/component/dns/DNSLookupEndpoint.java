/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.camel.component.dns;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Type;

/**
 * 
 *         An endpoint to manage lookup operations, using the API from dnsjava.
 * 
 */
public class DNSLookupEndpoint extends DefaultEndpoint {

    private static final String DNS_NAME = "dns.name";
    private static final String DNS_TYPE = "dns.type";
    private static final String DNS_CLASS = "dns.class";

    public DNSLookupEndpoint(CamelContext context) {
        super("dns://lookup", context);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }

    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {

            public void process(Exchange exchange) throws Exception {
                Object name = exchange.getIn().getHeader(DNS_NAME);
                if (name == null || "".equals(name)) {
                    throw new IllegalArgumentException("name is null or empty");
                }
                String dns_name = String.valueOf(name);
                Object type = exchange.getIn().getHeader(DNS_TYPE);
                Integer dns_type = null;
                if (type != null) {
                    dns_type = Type.value(String.valueOf(type));
                }
                Object dclass = exchange.getIn().getHeader(DNS_CLASS);
                Integer dns_class = null;
                if (dclass != null) {
                    dns_class = DClass.value(String.valueOf(dclass));
                }

                Lookup lookup = null;
                if (dns_type != null && dns_class != null) {
                    lookup = new Lookup(dns_name, dns_type, dns_class);
                } else if (dns_type != null) {
                    lookup = new Lookup(dns_name, dns_type);
                } else {
                    lookup = new Lookup(dns_name);
                }

                lookup.run();
                if (lookup.getAnswers() != null) {
                    exchange.getOut().setBody(lookup.getAnswers());
                } else {
                    exchange.getOut().setBody(lookup.getErrorString());
                }
            }

        };
    }

    public boolean isSingleton() {
        return false;
    }

}
