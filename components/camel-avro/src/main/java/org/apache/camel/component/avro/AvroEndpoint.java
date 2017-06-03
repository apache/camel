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
package org.apache.camel.component.avro;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Working with Apache Avro for data serialization.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "avro", title = "Avro", syntax = "avro:transport:host:port/messageName", consumerClass = AvroConsumer.class, label = "messaging,transformation")
public abstract class AvroEndpoint extends DefaultEndpoint implements AsyncEndpoint {

    @UriParam
    private AvroConfiguration configuration;

    /**
     * Constructs a fully-initialized DefaultEndpoint instance. This is the
     * preferred method of constructing an object from Java code (as opposed to
     * Spring beans, etc.).
     *
     * @param endpointUri the full URI used to create this endpoint
     * @param component   the component that created this endpoint
     */
    public AvroEndpoint(String endpointUri, Component component, AvroConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    public Exchange createExchange(Protocol.Message message, Object request) {
        ExchangePattern pattern = ExchangePattern.InOut;
        if (message.getResponse().equals(Schema.Type.NULL)) {
            pattern = ExchangePattern.InOnly;
        }
        Exchange exchange = createExchange(pattern);
        exchange.getIn().setBody(request);
        exchange.getIn().setHeader(AvroConstants.AVRO_MESSAGE_NAME, message.getName());
        return exchange;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
    
    /**
     * Creates a new <a
     * href="http://camel.apache.org/event-driven-consumer.html">Event
     * Driven Consumer</a> which consumes messages from the endpoint using the
     * given processor
     *
     * @param processor the given processor
     * @return a newly created consumer
     * @throws Exception can be thrown
     */
    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new AvroConsumer(this, processor);
    }

    public AvroConfiguration getConfiguration() {
        return configuration;
    }

}
