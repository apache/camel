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

package org.apache.camel.processor.resequencer;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronization facade for {@link ResequencerEngine} for testing purposes only. This facade is used for both
 * exclusion purposes and for visibility of changes performed by different threads in unit tests. This facade is
 * <i>not</i> needed in {@link ResequencerEngine} applications because it is expected that resequencing is performed by
 * a single thread.
 */
public class ResequencerEngineSync<E> {

    private final Lock lock = new ReentrantLock();
    private final ResequencerEngine<E> resequencer;

    public ResequencerEngineSync(ResequencerEngine<E> resequencer) {
        this.resequencer = resequencer;
    }

    public void stop() {
        lock.lock();
        try {
            resequencer.stop();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return resequencer.size();
        } finally {
            lock.unlock();
        }
    }

    public long getTimeout() {
        lock.lock();
        try {
            return resequencer.getTimeout();
        } finally {
            lock.unlock();
        }
    }

    public void setTimeout(long timeout) {
        lock.lock();
        try {
            resequencer.setTimeout(timeout);
        } finally {
            lock.unlock();
        }
    }

    public SequenceSender<E> getSequenceSender() {
        lock.lock();
        try {
            return resequencer.getSequenceSender();
        } finally {
            lock.unlock();
        }
    }

    public void setSequenceSender(SequenceSender<E> sequenceSender) {
        lock.lock();
        try {
            resequencer.setSequenceSender(sequenceSender);
        } finally {
            lock.unlock();
        }
    }

    E getLastDelivered() {
        lock.lock();
        try {
            return resequencer.getLastDelivered();
        } finally {
            lock.unlock();
        }
    }

    void setLastDelivered(E o) {
        lock.lock();
        try {
            resequencer.setLastDelivered(o);
        } finally {
            lock.unlock();
        }
    }

    public void insert(E o) {
        lock.lock();
        try {
            resequencer.insert(o);
        } finally {
            lock.unlock();
        }
    }

    public void deliver() throws Exception {
        lock.lock();
        try {
            resequencer.deliver();
        } finally {
            lock.unlock();
        }
    }

    public boolean deliverNext() throws Exception {
        lock.lock();
        try {
            return resequencer.deliverNext();
        } finally {
            lock.unlock();
        }
    }
}
