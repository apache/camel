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
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

/**
 *         An endpoint for dig-like operations over DNS adresses.
 * 
 *         Inspired from Dig.java coming with the distribution of dnsjava,
 *         though most if not all options are unsupported.
 */
public class DNSDigEndpoint extends DefaultEndpoint {

    private static final String DNS_SERVER = "dns.server";
    private static final String DNS_TYPE = "dns.type";
    private static final String DNS_CLASS = "dns.class";
    private static final String DNS_NAME = "dns.name";

    public DNSDigEndpoint(CamelContext ctxt) {
        super("dns://dig", ctxt);
    }

    public Consumer createConsumer(Processor arg0) throws Exception {
        throw new UnsupportedOperationException();
    }

    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {

            public void process(Exchange exchange) throws Exception {
                String server = null;
                if (exchange.getIn().getHeader(DNS_SERVER) != null) {
                    server = String.valueOf(exchange.getIn().
                            getHeader(DNS_SERVER));
                }
                SimpleResolver resolver = new SimpleResolver(server);
                int type = Type.value(String.valueOf(exchange.getIn()
                        .getHeader(DNS_TYPE)));
                if (type == -1) {
                    // default: if unparsable value given, use A.
                    type = Type.A;
                }
                int dclass = DClass.value(String.valueOf(exchange.getIn()
                        .getHeader(DNS_CLASS)));
                if (dclass == -1) {
                    // by default, value is IN.
                    dclass = DClass.IN;
                }
                Name name = Name.fromString(
                        String.valueOf(exchange.getIn().getHeader(DNS_NAME)),
                        Name.root);
                Record rec = Record.newRecord(name, type, dclass);
                Message query = Message.newQuery(rec);
                Message response = resolver.send(query);
                exchange.getOut().setBody(response);
            }
        };
    }

    public boolean isSingleton() {
        return false;
    }

}
