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

import java.net.InetAddress;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * An endpoint to conduct IP address lookup from the host name.
 */
public class DnsIpEndpoint extends DefaultEndpoint {

    public DnsIpEndpoint(Component component) {
        super("dns://ip", component);
    }

    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {

            public void process(Exchange exchange) throws Exception {
                String domain = exchange.getIn().getHeader(DnsConstants.DNS_DOMAIN, String.class);
                ObjectHelper.notEmpty(domain, "Header " + DnsConstants.DNS_DOMAIN);

                InetAddress address = InetAddress.getByName(domain);
                exchange.getIn().setBody(address);
            }
        };
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Creating a consumer is not supported");
    }

    public boolean isSingleton() {
        return false;
    }
}
