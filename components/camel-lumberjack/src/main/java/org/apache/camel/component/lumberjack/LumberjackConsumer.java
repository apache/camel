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
package org.apache.camel.component.lumberjack;

import java.util.concurrent.ThreadFactory;

import javax.net.ssl.SSLContext;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.lumberjack.io.LumberjackMessageProcessor;
import org.apache.camel.component.lumberjack.io.LumberjackServer;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.concurrent.CamelThreadFactory;

public class LumberjackConsumer extends DefaultConsumer {
    private final LumberjackServer lumberjackServer;

    public LumberjackConsumer(LumberjackEndpoint endpoint, Processor processor, String host, int port, SSLContext sslContext) {
        super(endpoint, processor);
        lumberjackServer = new LumberjackServer(host, port, sslContext, getThreadFactory(), this::onMessageReceived);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        lumberjackServer.start();
    }

    @Override
    protected void doStop() throws Exception {
        lumberjackServer.stop();
        super.doStop();
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();
        lumberjackServer.start();
    }

    @Override
    protected void doSuspend() throws Exception {
        lumberjackServer.stop();
        super.doSuspend();
    }

    private ThreadFactory getThreadFactory() {
        String threadNamePattern = getEndpoint().getCamelContext().getExecutorServiceManager().getThreadNamePattern();
        return new CamelThreadFactory(threadNamePattern, "LumberjackNettyExecutor", true);
    }

    private void onMessageReceived(Object payload, LumberjackMessageProcessor.Callback callback) {
        // Create the exchange
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setBody(payload);

        // Process the exchange
        getAsyncProcessor().process(exchange, doneSync -> callback.onComplete(!exchange.isFailed()));
    }
}
