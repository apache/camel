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
package org.apache.camel.component.xchange;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

@UriEndpoint(firstVersion = "2.21.0", scheme = "xchange", title = "XChange", syntax = "xchange:name", consumerClass = XChangeConsumer.class, label = "api")
public class XChangeEndpoint extends DefaultPollingEndpoint {

    @UriParam
    private XChangeConfiguration configuration;
    private final XChange exchange;
    
    public XChangeEndpoint(String uri, XChangeComponent component, XChangeConfiguration properties, XChange exchange) {
        super(uri, component);
        this.configuration = properties;
        this.exchange = exchange;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        XChangeConsumer answer = new XChangeConsumer(this, processor);

        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a feed, so we override
        // with a new default value. End user can override this value by providing a consumer.delay parameter
        answer.setDelay(XChangeConsumer.DEFAULT_CONSUMER_DELAY);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public XChangeConfiguration getConfiguration() {
        return configuration;
    }

    public XChange getXChange() {
        return exchange;
    }
}
