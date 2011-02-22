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

import java.net.InetAddress;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.xbill.DNS.Address;

/**
 * 
 *         An endpoint to conduct IP address lookup from the host name.
 * 
 */
public class DNSIPEndpoint extends DefaultEndpoint {

    public DNSIPEndpoint(CamelContext context) {
        super("dns://ip", context);
    }

    public Consumer createConsumer(Processor arg0) throws Exception {
        throw new UnsupportedOperationException();
    }

    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {

            public void process(Exchange exchange) throws Exception {
                Object domain = exchange.getIn().getHeader("dns.domain");
                if (!(domain instanceof String)
                        || ((String) domain).length() == 0) {
                    throw new IllegalArgumentException(
                            "Invalid or null domain :" + domain);
                }
                InetAddress address = Address.getByName((String) domain);
                exchange.getOut().setBody(address);
            }
        };
    }

    public boolean isSingleton() {
        return false;
    }

}
