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
package org.apache.camel.component.soroushbot.component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.service.SoroushService;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.soroushbot.utils.StringUtils.ordinal;

/**
 * this component handle logic for getting message from Soroush server and for each message
 * it calls abstract function {@link SoroushBotAbstractConsumer#sendExchange(Exchange)}
 * each subclass should handle how it will start the processing of the exchange
 */
public abstract class SoroushBotAbstractConsumer extends DefaultConsumer implements org.apache.camel.spi.ShutdownPrepared {

    private static final Logger LOG = LoggerFactory.getLogger(SoroushBotAbstractConsumer.class);

    SoroushBotEndpoint endpoint;
    /**
     * {@link ObjectMapper} for parse message JSON
     */
    ObjectMapper objectMapper = new ObjectMapper();
    boolean shutdown;
    long lastMessageReceived;
    private ReconnectableEventSourceListener connection;

    public SoroushBotAbstractConsumer(SoroushBotEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public void doStart() {
        run();
    }

    protected final void handleExceptionThrownWhileCreatingOrProcessingExchange(Exchange exchange, SoroushMessage soroushMessage, Exception ex) {
        //set originalMessage property to the created soroushMessage to let  Error Handler access the message
        exchange.setProperty("OriginalMessage", soroushMessage);
        //use this instead of handleException() to manually set the exchange.
        getExceptionHandler().handleException("message can not be processed due to :" + ex.getMessage(), exchange, ex);

    }

    /**
     * handle how processing of the exchange should be started
     *
     * @param exchange
     */
    protected abstract void sendExchange(Exchange exchange) throws Exception;

    private void run() {
        lastMessageReceived = System.currentTimeMillis();
        Request request = new Request.Builder()
                .url(SoroushService.get().generateUrl(endpoint.getAuthorizationToken(), SoroushAction.getMessage, null))
                .build();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(endpoint.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(0L, TimeUnit.MILLISECONDS)
                .readTimeout(0L, TimeUnit.MILLISECONDS)
                .build();
        connection = new ReconnectableEventSourceListener(client, request, endpoint.getMaxConnectionRetry()) {
            @Override
            protected boolean onBeforeConnect() {
                int connectionRetry = getConnectionRetry();
                try {
                    endpoint.waitBeforeRetry(connectionRetry);
                } catch (InterruptedException e) {
                    return false;
                }
                if (!shutdown) {
                    if (connectionRetry == 0) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("connecting to getMessage from soroush");
                        }
                    } else {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("connection is closed. retrying for the " + ordinal(connectionRetry) + " time(s)... ");
                        }
                    }
                }
                return !shutdown;
            }

            @Override
            public void onOpen(EventSource eventSource, Response response) {
                super.onOpen(eventSource, response);
                LOG.info("connection established");
            }

            @Override
            protected boolean handleClose(EventSource eventSource, boolean manuallyClosed) {
                if (!manuallyClosed) {
                    LOG.warn("connection got closed");
                } else {
                    LOG.debug("manually reconnecting to ensure we have live connection");
                }
                return true;
            }

            @Override
            protected boolean handleFailure(EventSource eventSource, boolean manuallyClosed, Throwable t, Response response) {
                if (!manuallyClosed) {
                    LOG.error("connection failed due to following error", t);
                } else {
                    LOG.debug("manually reconnecting to ensure we have live connection");
                }
                return true;
            }


            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                Exchange exchange = endpoint.createExchange();
                try {
                    SoroushMessage soroushMessage = objectMapper.readValue(data, SoroushMessage.class);
                    try {
                        exchange.getIn().setBody(soroushMessage);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("event data is: " + data);
                        }
                        // if autoDownload is true, download the resource if provided in the message
                        if (endpoint.isAutoDownload()) {
                            endpoint.handleDownloadFiles(soroushMessage);
                        }
                        //let each subclass decide how to start processing of each exchange
                        sendExchange(exchange);
                    } catch (Exception ex) {
                        handleExceptionThrownWhileCreatingOrProcessingExchange(exchange, soroushMessage, ex);
                    }
                } catch (IOException ex) {
                    LOG.error("can not parse data due to following error", ex);
                }
            }

            @Override
            public void onFinishProcess() {
                LOG.info("max connection retry reached! we are closing the endpoint!");
            }
        };
        connection.connect();
        endpoint.getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "health check")
                .scheduleAtFixedRate(() -> {
                    if (lastMessageReceived < System.currentTimeMillis() - endpoint.getReconnectIdleConnectionTimeout()) {
                        connection.close();
                    }
                }, 2000, endpoint.getReconnectIdleConnectionTimeout(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        if (!suspendOnly) {
            shutdown = true;
            connection.close();
        }
    }
}

class ReconnectableEventSourceListener extends EventSourceListener {
    private boolean manuallyClosed;
    private OkHttpClient client;
    private final int maxConnectionRetry;
    private int connectionRetry;
    private Request request;
    private final EventSource.Factory factory;
    private EventSource eventSource;

    public ReconnectableEventSourceListener(OkHttpClient client, Request request, int maxConnectionRetry) {
        this.client = client;
        this.maxConnectionRetry = maxConnectionRetry;
        this.request = request;
        factory = EventSources.createFactory(client);
    }

    public void reconnect() {
        if (!manuallyClosed) {
            connectionRetry++;
        } else {
            manuallyClosed = false;
        }
        if (eventSource != null) {
            eventSource.cancel();
        }
        connect();
    }

    public void connect() {

        if (!onBeforeConnect()) {
            return;
        }
        if (maxConnectionRetry >= connectionRetry || maxConnectionRetry < 0) {
            eventSource = factory.newEventSource(request, this);
        } else {
            onFinishProcess();
        }
    }

    public void close() {
        manuallyClosed = true;
        eventSource.cancel();
    }

    public void onFinishProcess() {
    }

    protected boolean onBeforeConnect() {
        return true;
    }

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        connectionRetry = 0;
    }

    @Override
    public final void onClosed(EventSource eventSource) {
        if (handleClose(eventSource, manuallyClosed)) {
            reconnect();
        }
    }

    protected boolean handleClose(EventSource eventSource, boolean manuallyClosed) {
        return true;
    }

    @Override
    public final void onFailure(EventSource eventSource, Throwable t, Response response) {
        if (handleFailure(eventSource, manuallyClosed, t, response)) {
            reconnect();
        }
    }

    protected boolean handleFailure(EventSource eventSource, boolean manuallyClosed, Throwable t, Response response) {
        return true;
    }

    public int getConnectionRetry() {
        return connectionRetry;
    }
}

