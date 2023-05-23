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
package org.apache.camel.component.mina;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Socket level networking using TCP or UDP with Apache Mina 2.x.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "mina", title = "Mina", syntax = "mina:protocol:host:port",
             category = { Category.NETWORKING }, headersClass = MinaConstants.class)
public class MinaEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    @UriParam
    private MinaConfiguration configuration;

    public MinaEndpoint() {
    }

    public MinaEndpoint(String endpointUri, Component component, MinaConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public boolean isSingletonProducer() {
        // the producer should not be singleton otherwise cannot use concurrent producers and safely
        // use request/reply with correct correlation
        return !configuration.isSync();
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        return new MinaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        MinaConsumer answer = new MinaConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        // only datagram should allow multiple consumers
        return configuration.isDatagramProtocol();
    }

    // Properties
    // -------------------------------------------------------------------------
    public MinaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(MinaConfiguration configuration) {
        this.configuration = configuration;
    }
}
