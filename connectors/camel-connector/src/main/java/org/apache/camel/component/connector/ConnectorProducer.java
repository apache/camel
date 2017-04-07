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

import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ServiceHelper;

/**
 * Connector {@link Producer} which is capable of performing before and after custom processing
 * while processing (ie sending the message).
 */
public class ConnectorProducer extends DefaultProducer {

    private final Producer producer;
    private final Processor beforeProducer;
    private final Processor afterProducer;

    public ConnectorProducer(Endpoint endpoint, Producer producer, Processor beforeProducer, Processor afterProducer) {
        super(endpoint);
        this.producer = producer;
        this.beforeProducer = beforeProducer;
        this.afterProducer = afterProducer;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!isRunAllowed()) {
            throw new RejectedExecutionException();
        }

        if (beforeProducer != null) {
            beforeProducer.process(exchange);
        }

        producer.process(exchange);

        if (afterProducer != null) {
            afterProducer.process(exchange);
        }
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
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
        ServiceHelper.stopAndShutdownService(producer);
    }
}
