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
package org.apache.camel.component.zookeeper.operations;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooKeeper;

import static java.lang.String.format;

/**
 * <code>FutureEventDrivenOperation</code> uses ZooKeepers {@link Watcher}
 * mechanism to await specific ZooKeeper events. Typically this is used to await
 * changes to a particular node before retrieving the change.
 */
public abstract class FutureEventDrivenOperation<ResultType> extends ZooKeeperOperation<ResultType> implements Watcher, WatchedEventProvider {

    private EventType[] awaitedTypes;

    private CountDownLatch waitForAnyWatchedType = new CountDownLatch(1);

    private WatchedEvent event;

    public FutureEventDrivenOperation(ZooKeeper connection, String node, EventType... awaitedTypes) {
        super(connection, node);
        this.awaitedTypes = awaitedTypes;
    }

    @Override
    public void process(WatchedEvent event) {
        this.event = event;
        EventType received = event.getType();
        if (LOG.isDebugEnabled()) {
            LOG.debug(format("Recieved event of type %s for node '%s'", received, event.getPath()));
        }

        for (EventType watched : awaitedTypes) {
            if (watched.equals(received)) {
                result = getResult();
                waitForAnyWatchedType.countDown();
            }
        }

        if (LOG.isTraceEnabled() && waitForAnyWatchedType.getCount() > 0) {

            StringBuilder b = new StringBuilder();
            for (EventType type : awaitedTypes) {
                b.append(type).append(", ");
            }
            if (b.length() > 0) {
                b.setLength(b.length() - 2);
            }
            LOG.trace(String.format("Recieved event of type %s did not match any watched types %s", received, Arrays.toString(awaitedTypes)));
        }
    }

    @Override
    public OperationResult<ResultType> get() throws InterruptedException, ExecutionException {
        installWatch();
        waitingThreads.add(Thread.currentThread());
        waitForAnyWatchedType.await();
        return result;
    }

    @Override
    public OperationResult<ResultType> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        installWatch();
        waitingThreads.add(Thread.currentThread());
        waitForAnyWatchedType.await(timeout, unit);
        return result;
    }

    /**
     * Install the watcher to receive {@link WatchedEvent}s. It should use the
     * appropriate asynchronous ZooKeeper call to do this so as not to block the
     * route from starting. Once one of the watched for types of event is
     * received a call is made to getResult, which can use the appropriate
     * synchronous call to retrieve the actual data.
     */
    protected abstract void installWatch();

    @Override
    public WatchedEvent getWatchedEvent() {
        return event;
    }

    public EventType[] getWatchedForTypes() {
        return awaitedTypes;
    }

}
