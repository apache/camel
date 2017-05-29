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
package org.apache.camel.component.seda;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Implementation of {@link BlockingQueueFactory} producing {@link java.util.concurrent.ArrayBlockingQueue}
 */
public class ArrayBlockingQueueFactory<E> implements BlockingQueueFactory<E> {

    /**
     * Capacity used when none provided
     */
    private int defaultCapacity = 50;

    /**
     * Lock fairness. null means default fairness
     */
    private Boolean fair;

    /**
     * @return Default array capacity
     */
    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    /**
     * @param defaultCapacity Default array capacity
     */
    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    /**
     * @return Lock fairness
     */
    public boolean isFair() {
        return fair;
    }

    /**
     * @param fair Lock fairness
     */
    public void setFair(boolean fair) {
        this.fair = fair;
    }

    @Override
    public ArrayBlockingQueue<E> create() {
        return create(defaultCapacity);
    }

    @Override
    public ArrayBlockingQueue<E> create(int capacity) {
        return fair == null
            ? new ArrayBlockingQueue<E>(defaultCapacity) : new ArrayBlockingQueue<E>(defaultCapacity, fair);
    }
}
