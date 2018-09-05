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
package org.apache.camel.component.reactive.streams;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * The Camel reactive-streams producer.
 */
public class ReactiveStreamsProducer extends DefaultAsyncProducer {

    private final ReactiveStreamsEndpoint endpoint;
    private final String name;
    private final CamelReactiveStreamsService service;

    public ReactiveStreamsProducer(ReactiveStreamsEndpoint endpoint, String name, CamelReactiveStreamsService service) {
        super(endpoint);

        this.endpoint = endpoint;
        this.name = ObjectHelper.notNull(name, "name");
        this.service = ObjectHelper.notNull(service, "service");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        ReactiveStreamsHelper.attachCallback(exchange, (data, error) -> {
            if (error != null) {
                data.setException(error);
            }

            callback.done(false);
        });

        service.sendCamelExchange(name, exchange);

        return false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.service.attachCamelProducer(endpoint.getStream(), this);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        this.service.detachCamelProducer(endpoint.getStream());
    }

    @Override
    public ReactiveStreamsEndpoint getEndpoint() {
        return endpoint;
    }

}
