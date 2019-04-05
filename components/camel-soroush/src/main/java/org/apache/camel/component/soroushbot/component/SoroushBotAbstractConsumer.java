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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.soroushbot.models.ConnectionType;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.service.SoroushService;
import org.apache.camel.support.DefaultConsumer;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.media.sse.EventInput;
import org.glassfish.jersey.media.sse.InboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import static org.apache.camel.component.soroushbot.utils.StringUtils.ordinal;

/**
 * this component handle logic for getting message from Soroush server and for each message
 * it calls abstract function {@link SoroushBotAbstractConsumer#sendExchange(Exchange)}
 * each subclass should handle how it will start the processing of the exchange
 */
abstract public class SoroushBotAbstractConsumer extends DefaultConsumer {
    SoroushBotEndpoint endpoint;
    /**
     * {@link ObjectMapper} for parse message JSON
     */
    ObjectMapper objectMapper = new ObjectMapper();

    public SoroushBotAbstractConsumer(SoroushBotEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    /**
     * create an {@link EventInput} that connect to soroush SSE server and read events
     *
     * @param target
     * @return
     */
    private EventInput createEvent(WebTarget target) {
        EventInput eventInput = target.request().get(EventInput.class);
        eventInput.setChunkType(MediaType.SERVER_SENT_EVENTS);
        return eventInput;
    }

    @Override
    public void doStart() {
//     create new Thread for listening to Soroush SSE Server so that it release the main camel thread.
        Thread thread = new Thread(() -> {
            Client client = ClientBuilder.newBuilder().register(SseFeature.class).build();
            client.property(ClientProperties.CONNECT_TIMEOUT, endpoint.connectionTimeout);
            WebTarget target = client.target(SoroushService.get().generateUrl(endpoint.authorizationToken, ConnectionType.getMessage, null));
            EventInput event = null;
            int retry = 0;
            //this while handle connectionRetry if connection failed or get closed.
            while (retry <= endpoint.maxConnectionRetry) {
                try {
                    if (event == null || event.isClosed()) {
                        if (retry == 0) {
                            if (log.isInfoEnabled()) log.info("connecting to getMessage from soroush");
                        } else {
                            if (log.isInfoEnabled())
                                log.info("connection is closed. retrying for the " + ordinal(retry) + " time(s)... ");
                        }
                        event = createEvent(target);
                    }
                    InboundEvent inboundEvent = event.read();
                    if (inboundEvent == null) {
                        if (log.isErrorEnabled()) log.error("can not read event");
                        event = null;
                        retry++;
                    } else {
                        //if read the message successfully then we reset the retry count to 0.
                        retry = 0;
                        Exchange exchange = endpoint.createExchange();
                        SoroushMessage soroushMessage = objectMapper.readValue(inboundEvent.getRawData(), SoroushMessage.class);
                        try {
                            exchange.getIn().setBody(soroushMessage);
                            if (log.isDebugEnabled())
                                log.debug("event data is: " + new String(inboundEvent.getRawData()));
                            // if autoDownload is true, download the resource if provided in the message
                            if (endpoint.autoDownload) {
                                endpoint.handleDownloadFiles(soroushMessage);
                            }
                            //let each subclass decide how to start processing of each exchange
                            sendExchange(exchange);
                        } catch (Exception ex) {
                            handleExceptionThrownWhileCreatingOrProcessingExchange(exchange, soroushMessage, ex);
                        }
                    }
                } catch (Exception ex) {
                    log.error("can not read data from soroush due to following error:", ex);
                    event = null;
                    retry++;
                }

            }
            //todo how to handle long connection failure
            log.info("max connection retry reached! we are closing the endpoint!");
        });
        thread.start();
        thread.setName("Soroush Receiver");
    }

    final protected void handleExceptionThrownWhileCreatingOrProcessingExchange(Exchange exchange, SoroushMessage soroushMessage, Exception ex) {
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

}

