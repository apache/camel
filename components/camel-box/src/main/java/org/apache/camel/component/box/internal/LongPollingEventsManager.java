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
package org.apache.camel.component.box.internal;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.box.boxjavalibv2.dao.BoxCollection;
import com.box.boxjavalibv2.dao.BoxEventCollection;
import com.box.boxjavalibv2.dao.BoxRealTimeServer;
import com.box.boxjavalibv2.dao.BoxTypedObject;
import com.box.boxjavalibv2.exceptions.AuthFatalFailureException;
import com.box.boxjavalibv2.exceptions.BoxServerException;
import com.box.boxjavalibv2.requests.requestobjects.BoxEventRequestObject;
import com.box.boxjavalibv2.resourcemanagers.IBoxEventsManager;
import com.box.restclientv2.exceptions.BoxRestException;
import com.box.restclientv2.exceptions.BoxSDKException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for monitoring events using long polling.
 */
@SuppressWarnings("deprecation")
public class LongPollingEventsManager {

    private static final Logger LOG = LoggerFactory.getLogger(LongPollingEventsManager.class);
    private static final String RETRY_TIMEOUT = "retry_timeout";
    private static final String MAX_RETRIES = "max_retries";
    private static final String MESSAGE = "message";
    private static final String NEW_CHANGE = "new_change";
    private static final String RECONNECT = "reconnect";
    private static final String OUT_OF_DATE = "out_of_date";

    private final CachedBoxClient cachedBoxClient;
    private final ExecutorService executorService;
    private final BasicHttpParams httpParams;

    private HttpClient httpClient;
    private Future<?> pollFuture;
    private HttpGet httpGet;
    private boolean done;

    public LongPollingEventsManager(CachedBoxClient boxClient,
                                    Map<String, Object> httpParams, ExecutorService executorService) {

        this.cachedBoxClient = boxClient;
        this.executorService = executorService;

        this.httpParams = new BasicHttpParams();
        HttpConnectionParams.setSoKeepalive(this.httpParams, true);

        if (httpParams != null) {
            for (Map.Entry<String, Object> entry : httpParams.entrySet()) {
                this.httpParams.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    public void poll(long streamPosition, final String streamType, final int limit, final EventCallback callback)
        throws BoxServerException, AuthFatalFailureException, BoxRestException {

        // get BoxClient Event Manager
        final IBoxEventsManager eventsManager = cachedBoxClient.getBoxClient().getEventsManager();

        // get current stream position if requested
        if (BoxEventRequestObject.STREAM_POSITION_NOW == streamPosition) {
            streamPosition = getCurrentStreamPosition(eventsManager, streamPosition);
        }

        // validate parameters
        ObjectHelper.notNull(streamPosition, "streamPosition");
        ObjectHelper.notEmpty(streamType, "streamType");
        ObjectHelper.notNull(callback, "eventCallback");

        httpClient = new DefaultHttpClient(cachedBoxClient.getClientConnectionManager(), httpParams);

        // start polling thread
        LOG.info("Started event polling thread for " + cachedBoxClient);

        final long startStreamPosition = streamPosition;
        pollFuture = executorService.submit(new Runnable() {
            @Override
            public void run() {

                final ObjectMapper mapper = new ObjectMapper();

                long currentStreamPosition = startStreamPosition;
                BoxRealTimeServer realTimeServer = null;

                boolean retry = false;
                int retries = 0;
                int maxRetries = 1;

                while (!done) {
                    try {
                        // set to true if no exceptions thrown
                        retry = false;

                        if (realTimeServer == null) {

                            // get RTS URL
                            realTimeServer = getBoxRealTimeServer(currentStreamPosition, eventsManager);

                            // update HTTP timeout
                            final int requestTimeout = Integer.parseInt(
                                realTimeServer.getExtraData(RETRY_TIMEOUT).toString());
                            final HttpParams params = httpClient.getParams();
                            HttpConnectionParams.setSoTimeout(params, requestTimeout * 1000);

                            // update maxRetries
                            maxRetries = Integer.parseInt(realTimeServer.getExtraData(MAX_RETRIES).toString());
                        }

                        // create HTTP request for RTS
                        httpGet = getPollRequest(realTimeServer.getUrl(), currentStreamPosition);

                        // execute RTS poll
                        HttpResponse httpResponse = null;
                        try {
                            httpResponse = httpClient.execute(httpGet, (HttpContext) null);
                        } catch (SocketTimeoutException e) {
                            LOG.debug("Poll timed out, retrying for " + cachedBoxClient);
                        }

                        if (httpResponse != null) {

                            // parse response
                            final StatusLine statusLine = httpResponse.getStatusLine();
                            if (statusLine != null && statusLine.getStatusCode() == HttpStatus.SC_OK) {
                                final HttpEntity entity = httpResponse.getEntity();
                                @SuppressWarnings("unchecked")
                                Map<String, String> rtsResponse = mapper.readValue(entity.getContent(), Map.class);

                                final String message = rtsResponse.get(MESSAGE);
                                if (NEW_CHANGE.equals(message)) {

                                    // get events
                                    final BoxEventRequestObject requestObject =
                                        BoxEventRequestObject.getEventsRequestObject(currentStreamPosition);
                                    requestObject.setStreamType(streamType);
                                    requestObject.setLimit(limit);
                                    final BoxEventCollection events = eventsManager.getEvents(requestObject);

                                    // notify callback
                                    callback.onEvent(events);

                                    // update stream position
                                    currentStreamPosition = events.getNextStreamPosition();

                                } else if (RECONNECT.equals(message) || MAX_RETRIES.equals(message)) {
                                    LOG.debug("Long poll reconnect for " + cachedBoxClient);
                                    realTimeServer = null;
                                } else if (OUT_OF_DATE.equals(message)) {
                                    // update currentStreamPosition
                                    LOG.debug("Long poll out of date for " + cachedBoxClient);
                                    currentStreamPosition = getCurrentStreamPosition(eventsManager,
                                        BoxEventRequestObject.STREAM_POSITION_NOW);
                                    realTimeServer = null;
                                } else {
                                    throw new RuntimeCamelException("Unknown poll response " + message);
                                }
                            } else {
                                String msg = "Unknown error";
                                if (statusLine != null) {
                                    msg = String.format("Error polling events for %s: code=%s, message=%s",
                                        cachedBoxClient, statusLine.getStatusCode(), statusLine.getReasonPhrase());
                                }
                                throw new RuntimeCamelException(msg);
                            }
                        }

                        // keep polling
                        retry = true;

                    } catch (InterruptedException e) {
                        LOG.debug("Interrupted event polling thread for {}, exiting...", cachedBoxClient);
                    } catch (BoxSDKException e) {
                        callback.onException(e);
                    } catch (RuntimeCamelException e) {
                        callback.onException(e);
                    } catch (SocketException e) {
                        // TODO handle connection aborts!!!
                        LOG.debug("Socket exception while event polling for {}", cachedBoxClient);
                        retry = true;
                        realTimeServer = null;
                    } catch (Exception e) {
                        callback.onException(new RuntimeCamelException("Error while polling for "
                            + cachedBoxClient + ": " + e.getMessage(), e));
                    } finally {
                        // are we done yet?
                        if (!retry) {
                            done = true;
                        } else {
                            if (realTimeServer != null
                                && (++retries > maxRetries)) {
                                // make another option call
                                realTimeServer = null;
                            }
                        }
                    }
                }
                LOG.info("Stopped event polling thread for " + cachedBoxClient);
            }
        });
    }

    private long getCurrentStreamPosition(IBoxEventsManager eventsManager, long streamPosition)
        throws BoxRestException, BoxServerException, AuthFatalFailureException {

        final BoxEventRequestObject requestObject =
            BoxEventRequestObject.getEventsRequestObject(streamPosition);
        final BoxEventCollection events = eventsManager.getEvents(requestObject);
        streamPosition = events.getNextStreamPosition();
        return streamPosition;
    }

    public void stopPolling() throws Exception {
        if (!done) {

            // done polling
            done = true;

            // make sure an HTTP GET is not in progress
            if (httpGet != null && !httpGet.isAborted()) {
                httpGet.abort();
            }

            // cancel polling thread
            if (pollFuture.cancel(true)) {
                LOG.info("Stopped event polling for " + cachedBoxClient);
            } else {
                LOG.warn("Unable to stop event polling for " + cachedBoxClient);
            }

            httpClient = null;
            pollFuture = null;
        }
    }

    private BoxRealTimeServer getBoxRealTimeServer(long currentStreamPosition, IBoxEventsManager eventsManager)
        throws BoxRestException, BoxServerException, AuthFatalFailureException {

        final BoxEventRequestObject optionsRequest =
            BoxEventRequestObject.getEventsRequestObject(currentStreamPosition);

        final BoxCollection eventOptions = eventsManager.getEventOptions(optionsRequest);
        final ArrayList<BoxTypedObject> entries = eventOptions.getEntries();

        // validate options
        if (entries == null || entries.size() < 1
            || !(entries.get(0) instanceof BoxRealTimeServer)) {

            throw new RuntimeCamelException("No Real Time Server from event options for " + cachedBoxClient);
        }

        return (BoxRealTimeServer) entries.get(0);
    }

    private HttpGet getPollRequest(String url, long currentStreamPosition) throws AuthFatalFailureException {

        final HttpGet httpGet = new HttpGet(url + "&stream_position=" + currentStreamPosition);
        final String accessToken = cachedBoxClient.getBoxClient().getAuthData().getAccessToken();
        httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return httpGet;
    }

}
