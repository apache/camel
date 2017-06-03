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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.util.ServiceHelper;

/**
 * Connector {@link Processor} which is capable of performing before and after custom processing
 * while consuming a message (ie from the consumer).
 */
public class ConnectorConsumerProcessor extends DelegateAsyncProcessor {

    private final Processor beforeConsumer;
    private final Processor afterConsumer;

    public ConnectorConsumerProcessor(Processor processor, Processor beforeConsumer, Processor afterConsumer) {
        super(processor);
        this.beforeConsumer = beforeConsumer;
        this.afterConsumer = afterConsumer;
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        // setup callback for after consumer
        AsyncCallback delegate = doneSync -> {
            if (afterConsumer != null) {
                try {
                    afterConsumer.process(exchange);
                } catch (Throwable e) {
                    exchange.setException(e);
                }
            }
            callback.done(doneSync);
        };

        // perform any before consumer
        if (beforeConsumer != null) {
            try {
                beforeConsumer.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        // process the consumer
        return super.process(exchange, delegate);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(beforeConsumer, processor, afterConsumer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(beforeConsumer, processor, afterConsumer);
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(processor);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(beforeConsumer, processor, afterConsumer);
    }

}
