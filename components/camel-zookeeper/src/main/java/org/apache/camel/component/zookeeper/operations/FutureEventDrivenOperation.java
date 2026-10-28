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

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;

import static java.lang.String.format;

/**
 * <code>FutureEventDrivenOperation</code> uses ZooKeepers {@link Watcher} mechanism to await specific ZooKeeper events.
 * Typically this is used to await changes to a particular node before retrieving the change.
 */
public abstract class FutureEventDrivenOperation<ResultType> extends ZooKeeperOperation<ResultType>
        implements Watcher, WatchedEventProvider {

    private EventType[] awaitedTypes;

    private CountDownLatch waitForAnyWatchedType = new CountDownLatch(1);

    private WatchedEvent event;

    private volatile ExecutionException failure;

    protected FutureEventDrivenOperation(ZooKeeper connection, String node, EventType... awaitedTypes) {
        super(connection, node);
        this.awaitedTypes = awaitedTypes;
    }

    @Override
    public void process(WatchedEvent event) {
        this.event = event;
        EventType received = event.getType();
        if (LOG.isDebugEnabled()) {
            LOG.debug(format("Received event of type %s for node '%s'", received, event.getPath()));
        }

        // When the session expires, ZooKeeper tells every watcher with an event of type None and state Expired. The
        // awaited event can no longer come (the handle is closed for good), so stop waiting and fail.
        if (received == EventType.None && event.getState() == KeeperState.Expired) {
            failure = new ExecutionException(
                    "The ZooKeeper session expired while waiting for an event on node '" + getNode() + "'",
                    new KeeperException.SessionExpiredException());
            waitForAnyWatchedType.countDown();
            return;
        }

        for (EventType watched : awaitedTypes) {
            if (watched.equals(received)) {
                result = getResult();
                waitForAnyWatchedType.countDown();
            }
        }

        if (LOG.isTraceEnabled() && waitForAnyWatchedType.getCount() > 0) {

            LOG.trace(String.format("Received event of type %s did not match any watched types %s", received,
                    Arrays.toString(awaitedTypes)));
        }
    }

    @Override
    public OperationResult<ResultType> get() throws ExecutionException, InterruptedException {
        installWatch();
        waitingThreads.add(Thread.currentThread());
        waitForAnyWatchedType.await();
        throwIfFailed();
        return result;
    }

    @Override
    public OperationResult<ResultType> get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        installWatch();
        waitingThreads.add(Thread.currentThread());
        waitForAnyWatchedType.await(timeout, unit);
        throwIfFailed();
        return result;
    }

    private void throwIfFailed() throws ExecutionException {
        ExecutionException e = failure;
        if (e != null) {
            throw e;
        }
    }

    /**
     * To be called from the asynchronous callback of the request made by {@link #installWatch()}, with its result code.
     * When the request failed (for example with SESSIONEXPIRED on a handle whose session expired, or CONNECTIONLOSS),
     * ZooKeeper did not register the watch, so the awaited event will never come: the operation stops waiting and fails
     * with the {@link KeeperException} of that result code. If the awaited event was already received there is nothing
     * left to wait for, so the failure of the request (for example NONODE because the node was deleted in the meantime)
     * is ignored.
     *
     * @param rc the result code of the request
     */
    protected void watchRequestCompleted(int rc) {
        if (rc != KeeperException.Code.OK.intValue() && waitForAnyWatchedType.getCount() > 0) {
            failure = new ExecutionException(
                    "Could not watch node '" + getNode() + "'",
                    KeeperException.create(KeeperException.Code.get(rc), getNode()));
            waitForAnyWatchedType.countDown();
        }
    }

    /**
     * Install the watcher to receive {@link WatchedEvent}s. It should use the appropriate asynchronous ZooKeeper call
     * to do this so as not to block the route from starting. Once one of the watched for types of event is received a
     * call is made to getResult, which can use the appropriate synchronous call to retrieve the actual data. The
     * callback of the asynchronous call must pass its result code to {@link #watchRequestCompleted(int)}.
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
