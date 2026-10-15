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
package org.apache.camel.component.docker.consumer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.model.Event;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.docker.DockerClientFactory;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerEndpoint;
import org.apache.camel.component.docker.DockerHelper;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerEventsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DockerEventsConsumer.class);

    /**
     * The delay before the consumer subscribes again after its event stream failed or ended.
     */
    static final long SUBSCRIBE_AGAIN_DELAY_MILLIS = 1000;

    /**
     * The maximum delay between two attempts to subscribe again, when subscribing keeps failing (for example while the
     * Docker daemon cannot be reached).
     */
    static final long SUBSCRIBE_AGAIN_MAX_DELAY_MILLIS = 30000;

    private DockerEndpoint endpoint;
    private DockerComponent component;
    private volatile EventsCmd eventsCmd;
    /**
     * The callback of the current event stream. docker-java opens the stream on its own thread and the stream belongs
     * to this callback: closing the command does not close it.
     */
    private volatile EventsCallback callback;
    private ScheduledExecutorService executor;

    public DockerEventsConsumer(DockerEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.component = (DockerComponent) endpoint.getComponent();

    }

    @Override
    public DockerEndpoint getEndpoint() {
        return (DockerEndpoint) super.getEndpoint();
    }

    /**
     * Determine the point in time to begin streaming events
     */
    private long processInitialEvent() {
        long currentTime = System.currentTimeMillis();
        Long initialRange
                = DockerHelper.getProperty(DockerConstants.DOCKER_INITIAL_RANGE, endpoint.getConfiguration(), null, Long.class);
        if (initialRange != null) {
            currentTime = currentTime - initialRange;
        }

        return currentTime;
    }

    @Override
    protected void doStart() throws Exception {
        executor = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                "DockerEventsConsumer");
        try {
            subscribe(String.valueOf(processInitialEvent()), SUBSCRIBE_AGAIN_DELAY_MILLIS);
        } catch (Exception e) {
            // a failed start does not call doStop()
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
            throw e;
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
        EventsCallback current = callback;
        callback = null;
        if (current != null) {
            current.close();
        }
        if (eventsCmd != null) {
            eventsCmd.close();
        }

        super.doStop();
    }

    /**
     * Opens a new event stream.
     *
     * @param since      the value of the since parameter, or <tt>null</tt> to receive the events from now on
     * @param retryDelay the delay before subscribing again if this stream fails before it is open
     */
    private void subscribe(String since, long retryDelay) {
        EventsCmd cmd = DockerClientFactory.getDockerClient(component, endpoint.getConfiguration(), null).eventsCmd();
        if (since != null) {
            cmd = cmd.withSince(since);
        }
        EventsCallback newCallback = new EventsCallback(retryDelay);
        this.eventsCmd = cmd;
        this.callback = newCallback;
        cmd.exec(newCallback);
        if (!isRunAllowed()) {
            // the consumer was stopped while it subscribed again, so stopping could not close this stream
            callback = null;
            newCallback.closeQuietly();
        }
    }

    /**
     * Subscribes again after the delay, as long as the consumer runs. The events of the time without a stream are not
     * received: the new stream starts with the events from then on.
     */
    private void subscribeAgain(long delayMillis) {
        ScheduledExecutorService current = executor;
        if (!isRunAllowed() || current == null) {
            return;
        }
        try {
            current.schedule(() -> {
                if (!isRunAllowed()) {
                    return;
                }
                long nextDelay = Math.min(delayMillis * 2, SUBSCRIBE_AGAIN_MAX_DELAY_MILLIS);
                try {
                    subscribe(null, nextDelay);
                } catch (Exception e) {
                    LOG.warn("Failed to subscribe again to the Docker events of {}, trying again in {} ms: {}",
                            endpoint, nextDelay, e.getMessage(), e);
                    subscribeAgain(nextDelay);
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            LOG.debug("Cannot subscribe again to the Docker events of {} as the consumer is stopping", endpoint, e);
        }
    }

    protected class EventsCallback extends ResultCallback.Adapter<Event> {

        /**
         * The delay before subscribing again when this stream fails before it is open. Once it is open, it is the
         * initial delay again.
         */
        private final long retryDelay;
        private volatile boolean open;
        private volatile boolean closed;

        protected EventsCallback() {
            this(SUBSCRIBE_AGAIN_DELAY_MILLIS);
        }

        /**
         * @param retryDelay the delay before subscribing again when this stream fails before it is open
         */
        EventsCallback(long retryDelay) {
            this.retryDelay = retryDelay;
        }

        @Override
        public void onStart(Closeable stream) {
            super.onStart(stream);
            open = true;
            if (closed) {
                // closed (the consumer was stopped) while the stream was being opened: close the stream now
                closeQuietly();
            }
        }

        @Override
        public void onNext(Event event) {
            if (closed) {
                // an event of a stream the consumer no longer reads
                return;
            }
            LOG.debug("Received Docker Event: {}", event);

            final Exchange exchange = createExchange(true);
            Message message = exchange.getIn();
            message.setBody(event);

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
        }

        @Override
        public void onError(Throwable throwable) {
            if (closed) {
                // closed by the consumer, which makes the read of the stream fail
                return;
            }
            // not super.onError(), which logs an ERROR with the stack trace for every failed attempt
            closeQuietly();
            if (callback != this || !isRunAllowed()) {
                return;
            }
            long delay = open ? SUBSCRIBE_AGAIN_DELAY_MILLIS : retryDelay;
            if (open) {
                LOG.warn("The Docker event stream of {} failed, subscribing again in {} ms: {}", endpoint, delay,
                        throwable.getMessage(), throwable);
            } else {
                LOG.warn("Failed to subscribe to the Docker events of {}, trying again in {} ms: {}", endpoint, delay,
                        throwable.getMessage());
            }
            subscribeAgain(delay);
        }

        @Override
        public void onComplete() {
            if (closed) {
                return;
            }
            closeQuietly();
            if (callback != this || !isRunAllowed()) {
                return;
            }
            long delay = open ? SUBSCRIBE_AGAIN_DELAY_MILLIS : retryDelay;
            LOG.warn("The Docker event stream of {} ended, subscribing again in {} ms", endpoint, delay);
            subscribeAgain(delay);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private void closeQuietly() {
            try {
                close();
            } catch (IOException e) {
                LOG.debug("Error closing the Docker event stream of {}", endpoint, e);
            }
        }
    }
}
