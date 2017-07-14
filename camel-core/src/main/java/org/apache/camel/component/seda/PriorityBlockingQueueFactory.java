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

import java.util.Comparator;

import java.util.concurrent.PriorityBlockingQueue;

/**
 * Implementation of {@link BlockingQueueFactory} producing {@link java.util.concurrent.PriorityBlockingQueue}
 */
public class PriorityBlockingQueueFactory<E> implements BlockingQueueFactory<E> {

    /**
     * Comparator used to sort exchanges
     */
    private Comparator<E> comparator;

    public Comparator<E> getComparator() {
        return comparator;
    }

    public void setComparator(Comparator<E> comparator) {
        this.comparator = comparator;
    }

    @Override
    public PriorityBlockingQueue<E> create() {
        return comparator == null 
            ? new PriorityBlockingQueue<E>()
            // PriorityQueue as a default capacity of 11
            : new PriorityBlockingQueue<E>(11, comparator);
    }

    @Override
    public PriorityBlockingQueue<E> create(int capacity) {
        return comparator == null
            ? new PriorityBlockingQueue<E>(capacity)
            : new PriorityBlockingQueue<E>(capacity, comparator);
    }
}
