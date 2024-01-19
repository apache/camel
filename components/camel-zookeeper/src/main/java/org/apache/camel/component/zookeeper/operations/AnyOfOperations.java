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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.component.zookeeper.ZooKeeperUtils;
import org.apache.zookeeper.WatchedEvent;

/**
 * <code>AnyOfOperations</code> is a composite operation of one or more sub operation, executing each in turn until any
 * one succeeds. If any execute successfully, this operation succeeds; if the sub operations are all executed without
 * success it fails.
 * <p>
 * It is mostly used for test and watch scenarios where a node is tested for existence, data or children, falling back
 * to a corresponding watch operation if the test operation fails.
 */
@SuppressWarnings("rawtypes")
public class AnyOfOperations extends ZooKeeperOperation implements WatchedEventProvider {

    private ZooKeeperOperation[] keeperOperations;
    private ZooKeeperOperation operationProvidingResult;

    public AnyOfOperations(String node, ZooKeeperOperation... keeperOperations) {
        super(null, node, false);
        this.keeperOperations = keeperOperations;
    }

    @Override
    public OperationResult get() throws InterruptedException, ExecutionException {
        for (ZooKeeperOperation op : keeperOperations) {
            try {
                OperationResult result = op.get();
                if (result.isOk()) {
                    operationProvidingResult = op;
                    return result;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
            }
        }
        throw new ExecutionException("All operations exhausted without one result", null);
    }

    @Override
    public OperationResult get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return get();
    }

    @Override
    public OperationResult getResult() {
        return null;
    }

    @Override
    public ZooKeeperOperation createCopy() throws Exception {
        ZooKeeperOperation[] copy = new ZooKeeperOperation[keeperOperations.length];
        for (int x = 0; x < keeperOperations.length; x++) {
            copy[x] = keeperOperations[x].createCopy();
        }
        return new AnyOfOperations(node, copy);
    }

    @Override
    public WatchedEvent getWatchedEvent() {
        return ZooKeeperUtils.getWatchedEvent(operationProvidingResult);
    }
}
