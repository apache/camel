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
package org.apache.camel.component.connector;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Connector {@link Producer} which is capable of performing before and after custom processing
 * while processing (ie sending the message).
 */
public class ConnectorProducer extends DefaultAsyncProducer {

    private final AsyncProcessor producer;
    private final Processor beforeProducer;
    private final Processor afterProducer;

    public ConnectorProducer(Endpoint endpoint, Producer producer, Processor beforeProducer, Processor afterProducer) {
        super(endpoint);
        this.producer = AsyncProcessorConverterHelper.convert(producer);
        this.beforeProducer = beforeProducer;
        this.afterProducer = afterProducer;
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        // setup callback for after producer
        AsyncCallback delegate = doneSync -> {
            if (afterProducer != null) {
                try {
                    afterProducer.process(exchange);
                } catch (Throwable e) {
                    exchange.setException(e);
                }
            }
            callback.done(doneSync);
        };

        // perform any before producer
        if (beforeProducer != null) {
            try {
                beforeProducer.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        return producer.process(exchange, delegate);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(beforeProducer, producer, afterProducer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(beforeProducer, producer, afterProducer);
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(producer);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(producer);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(beforeProducer, producer, afterProducer);
    }
}
