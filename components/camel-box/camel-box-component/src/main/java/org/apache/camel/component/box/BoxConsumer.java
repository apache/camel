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
package org.apache.camel.component.box;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxEvent;
import com.box.sdk.EventListener;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.box.api.BoxEventsManager;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.support.component.AbstractApiConsumer;
import org.apache.camel.support.component.ApiConsumerHelper;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Box consumer.
 */
public class BoxConsumer extends AbstractApiConsumer<BoxApiName, BoxConfiguration> implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(BoxConsumer.class);

    private static final String LISTENER_PROPERTY = "listener";
    private static final String STARTING_POSITION_PROPERTY = "startingPosition";
    // the delay before the events are listened to again after the event stream failed, doubled up to the maximum while
    // it keeps failing
    private static final long RESTART_DELAY = 1000;
    private static final long MAX_RESTART_DELAY = 60000;

    private BoxAPIConnection boxConnection;
    private BoxEventsManager apiProxy;
    private final ApiMethod apiMethod;
    private final Map<String, Object> properties;
    // guards the event stream between a restart and a stop
    private final Lock lock = new ReentrantLock();
    private ScheduledExecutorService restartExecutor;
    // the position after the last events received: a restart continues from there, so the events of the outage are
    // not skipped
    private volatile Long nextPosition;
    // guarded by the lock
    private long restartDelay = RESTART_DELAY;

    public BoxConsumer(BoxEndpoint endpoint, Processor processor) {
        super(endpoint, processor);

        apiMethod = ApiConsumerHelper.findMethod(endpoint, this);

        // Add listener property to register this consumer as listener for events.
        properties = new HashMap<>();
        properties.putAll(endpoint.getEndpointProperties());
        properties.put(LISTENER_PROPERTY, this);
    }

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        propertyNames.add(LISTENER_PROPERTY);
    }

    /**
     * The events are received from the event stream that {@link #doStart()} listens to, not polled: a poll of the API
     * consumer would call the listen method again without this consumer as listener (which does nothing and returns no
     * result), and route an exchange with no body for that result, on every poll.
     */
    @Override
    protected int poll() throws Exception {
        // the event stream is listened to, so the consumer is ready
        forceConsumerAsReady();
        return 0;
    }

    @Override
    public void onEvent(BoxEvent event) {
        try {
            ApiConsumerHelper.getResultsProcessed(this, event, false);
        } catch (Exception e) {
            LOG.warn("Received exception consuming event: ", e);
        }
    }

    @Override
    public void onNextPosition(long position) {
        nextPosition = position;
        lock.lock();
        try {
            // the event stream works again
            restartDelay = RESTART_DELAY;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called by the event stream when it fails (for example when Box cannot be reached for a while). The event stream
     * has stopped itself by then and is never started again by the Box SDK: listen again after a delay, else the
     * consumer stays started without receiving any event.
     */
    @Override
    public boolean onException(Throwable e) {
        getExceptionHandler().handleException(RuntimeCamelException.wrapRuntimeCamelException(e));
        scheduleRestart();
        return true;
    }

    private void scheduleRestart() {
        lock.lock();
        try {
            if (!isRunAllowed() || restartExecutor == null) {
                return;
            }
            long delay = restartDelay;
            restartDelay = Math.min(restartDelay * 2, MAX_RESTART_DELAY);
            LOG.info("The Box event stream stopped, listening for events again in {} ms", delay);
            restartExecutor.schedule(this::restart, delay, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    private void restart() {
        lock.lock();
        try {
            if (!isRunAllowed()) {
                return;
            }
            Long position = nextPosition != null ? nextPosition : startingPosition();
            apiProxy.stopListening();
            apiProxy.listen(this, position);
            LOG.info("Listening for Box events again from position {}", position != null ? position : "now");
        } catch (Exception e) {
            // such as Box still not reachable when the event stream starts
            LOG.debug("Cannot listen for Box events again", e);
            scheduleRestart();
        } finally {
            lock.unlock();
        }
    }

    private Long startingPosition() {
        Object value = properties.get(STARTING_POSITION_PROPERTY);
        return value != null ? getEndpoint().getCamelContext().getTypeConverter().convertTo(Long.class, value) : null;
    }

    @Override
    public BoxEndpoint getEndpoint() {
        return (BoxEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        boxConnection = getEndpoint().getBoxConnection();
        nextPosition = null;
        restartDelay = RESTART_DELAY;
        restartExecutor = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "BoxEventsRestart");
        apiProxy = new BoxEventsManager(boxConnection);
        // invoke the API method to start listening
        lock.lock();
        try {
            ApiMethodHelper.invokeMethod(apiProxy, apiMethod, properties);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        lock.lock();
        try {
            if (restartExecutor != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(restartExecutor);
                restartExecutor = null;
            }
            if (apiProxy != null) {
                apiProxy.stopListening();
            }
        } finally {
            lock.unlock();
        }
        super.doStop();
    }

}
