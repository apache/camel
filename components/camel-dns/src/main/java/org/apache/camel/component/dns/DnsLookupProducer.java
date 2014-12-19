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

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Type;

/**
 * A producer to manage lookup operations, using the API from dnsjava.
 */
public class DnsLookupProducer extends DefaultProducer {

    public DnsLookupProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String dnsName = exchange.getIn().getHeader(DnsConstants.DNS_NAME, String.class);
        ObjectHelper.notEmpty(dnsName, "Header " + DnsConstants.DNS_NAME);

        Object type = exchange.getIn().getHeader(DnsConstants.DNS_TYPE);
        Integer dnsType = null;
        if (type != null) {
            dnsType = Type.value(String.valueOf(type));
        }
        Object dclass = exchange.getIn().getHeader(DnsConstants.DNS_CLASS);
        Integer dnsClass = null;
        if (dclass != null) {
            dnsClass = DClass.value(String.valueOf(dclass));
        }

        Lookup lookup = (dnsClass == null)
                ? (dnsType == null ? new Lookup(dnsName) : new Lookup(dnsName, dnsType))
                : new Lookup(dnsName, dnsType, dnsClass);

        lookup.run();
        if (lookup.getAnswers() != null) {
            exchange.getIn().setBody(lookup.getAnswers());
        } else {
            throw new CamelException(lookup.getErrorString());
        }
    }
}
