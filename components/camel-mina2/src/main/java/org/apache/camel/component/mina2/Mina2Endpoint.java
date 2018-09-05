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
package org.apache.camel.component.mina2;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.apache.mina.core.session.IoSession;

/**
 * Socket level networking using TCP or UDP with the Apache Mina 2.x library.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "mina2", title = "Mina2", syntax = "mina2:protocol:host:port", consumerClass = Mina2Consumer.class, label = "networking,tcp,udp")
public class Mina2Endpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    @UriParam
    private Mina2Configuration configuration;

    public Mina2Endpoint() {
    }

    public Mina2Endpoint(String endpointUri, Component component, Mina2Configuration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        return new Mina2Producer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        Mina2Consumer answer = new Mina2Consumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Exchange createExchange(IoSession session, Object payload) {
        Exchange exchange = createExchange();
        exchange.getIn().setHeader(Mina2Constants.MINA_IOSESSION, session);
        exchange.getIn().setHeader(Mina2Constants.MINA_LOCAL_ADDRESS, session.getLocalAddress());
        exchange.getIn().setHeader(Mina2Constants.MINA_REMOTE_ADDRESS, session.getRemoteAddress());
        Mina2PayloadHelper.setIn(exchange, payload);
        return exchange;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        // only datagram should allow multiple consumers
        return configuration.isDatagramProtocol();
    }

    // Properties
    // -------------------------------------------------------------------------
    public Mina2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Mina2Configuration configuration) {
        this.configuration = configuration;
    }
}
