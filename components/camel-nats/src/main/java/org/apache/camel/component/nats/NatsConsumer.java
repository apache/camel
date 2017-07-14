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
package org.apache.camel.component.nats;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import io.nats.client.Connection;
import io.nats.client.ConnectionFactory;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NatsConsumer.class);

    private final Processor processor;
    private ExecutorService executor;
    private Connection connection;
    private Subscription sid;
    private boolean subscribed;

    public NatsConsumer(NatsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public NatsEndpoint getEndpoint() {
        return (NatsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting Nats Consumer");
        executor = getEndpoint().createExecutor();

        LOG.debug("Getting Nats Connection");
        connection = getConnection();

        executor.submit(new NatsConsumingTask(connection, getEndpoint().getNatsConfiguration()));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (getEndpoint().getNatsConfiguration().isFlushConnection()) {
            LOG.debug("Flushing Messages before stopping");
            connection.flush(getEndpoint().getNatsConfiguration().getFlushTimeout());
        }
        
        try {
            sid.unsubscribe();
        } catch (Exception e) {
            getExceptionHandler().handleException("Error during unsubscribing", e);
        }

        LOG.debug("Stopping Nats Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
        
        LOG.debug("Closing Nats Connection");
        if (!connection.isClosed()) {
            connection.close();   
        }
    }

    private Connection getConnection() throws IOException, InterruptedException, TimeoutException, GeneralSecurityException {
        Properties prop = getEndpoint().getNatsConfiguration().createProperties();
        ConnectionFactory factory = new ConnectionFactory(prop);
        if (getEndpoint().getNatsConfiguration().getSslContextParameters() != null && getEndpoint().getNatsConfiguration().isSecure()) {
            SSLContext sslCtx = getEndpoint().getNatsConfiguration().getSslContextParameters().createSSLContext(getEndpoint().getCamelContext()); 
            factory.setSSLContext(sslCtx);
            if (getEndpoint().getNatsConfiguration().isTlsDebug()) {
                factory.setTlsDebug(getEndpoint().getNatsConfiguration().isTlsDebug());
            }
        }
        connection = factory.createConnection();
        return connection;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    class NatsConsumingTask implements Runnable {

        private final Connection connection;
        private final NatsConfiguration configuration;

        NatsConsumingTask(Connection connection, NatsConfiguration configuration) {
            this.connection = connection;
            this.configuration = configuration;
        }

        @Override
        public void run() {
            try {
                if (ObjectHelper.isNotEmpty(configuration.getQueueName())) {
                    sid = connection.subscribe(getEndpoint().getNatsConfiguration().getTopic(), getEndpoint().getNatsConfiguration().getQueueName(), new MessageHandler() {
                        @Override
                        public void onMessage(Message msg) {
                            LOG.debug("Received Message: {}", msg);
                            Exchange exchange = getEndpoint().createExchange();
                            exchange.getIn().setBody(msg);
                            exchange.getIn().setHeader(NatsConstants.NATS_MESSAGE_TIMESTAMP, System.currentTimeMillis());
                            exchange.getIn().setHeader(NatsConstants.NATS_SUBSCRIPTION_ID, sid);
                            try {
                                processor.process(exchange);
                            } catch (Exception e) {
                                getExceptionHandler().handleException("Error during processing", exchange, e);
                            }
                        }
                    });
                    if (ObjectHelper.isNotEmpty(getEndpoint().getNatsConfiguration().getMaxMessages())) {
                        sid.autoUnsubscribe(Integer.parseInt(getEndpoint().getNatsConfiguration().getMaxMessages()));
                    }
                    if (sid.isValid()) {
                        setSubscribed(true);
                    }
                } else {
                    sid = connection.subscribe(getEndpoint().getNatsConfiguration().getTopic(), new MessageHandler() {
                        @Override
                        public void onMessage(Message msg) {
                            LOG.debug("Received Message: {}", msg);
                            Exchange exchange = getEndpoint().createExchange();
                            exchange.getIn().setBody(msg);
                            exchange.getIn().setHeader(NatsConstants.NATS_MESSAGE_TIMESTAMP, System.currentTimeMillis());
                            exchange.getIn().setHeader(NatsConstants.NATS_SUBSCRIPTION_ID, sid);
                            try {
                                processor.process(exchange);
                            } catch (Exception e) {
                                getExceptionHandler().handleException("Error during processing", exchange, e);
                            }
                        }
                    });
                    if (ObjectHelper.isNotEmpty(getEndpoint().getNatsConfiguration().getMaxMessages())) {
                        sid.autoUnsubscribe(Integer.parseInt(getEndpoint().getNatsConfiguration().getMaxMessages()));
                    }
                    if (sid.isValid()) {
                        setSubscribed(true);
                    }
                }
            } catch (Throwable e) {
                getExceptionHandler().handleException("Error during processing", e);
            }
        }
    }

}
