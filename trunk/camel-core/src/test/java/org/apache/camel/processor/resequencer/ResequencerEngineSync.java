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
package org.apache.camel.processor.resequencer;

/**
 * Synchronization facade for {@link ResequencerEngine} for testing purposes
 * only. This facade is used for both exclusion purposes and for visibility of
 * changes performed by different threads in unit tests. This facade is <i>not</i>
 * needed in {@link ResequencerEngine} applications because it is expected that
 * resequencing is performed by a single thread.
 */
public class ResequencerEngineSync<E> {

    private ResequencerEngine<E> resequencer;
    
    public ResequencerEngineSync(ResequencerEngine<E> resequencer) {
        this.resequencer = resequencer;
    }
    
    public synchronized void stop() {
        resequencer.stop();
    }
    
    public synchronized int size() {
        return resequencer.size();
    }
    
    public synchronized long getTimeout() {
        return resequencer.getTimeout();
    }

    public synchronized void setTimeout(long timeout) {
        resequencer.setTimeout(timeout);
    }

    public synchronized SequenceSender<E> getSequenceSender() {
        return resequencer.getSequenceSender();
    }

    public synchronized void setSequenceSender(SequenceSender<E> sequenceSender) {
        resequencer.setSequenceSender(sequenceSender);
    }

    synchronized E getLastDelivered() {
        return resequencer.getLastDelivered();
    }
    
    synchronized void setLastDelivered(E o) {
        resequencer.setLastDelivered(o);
    }
    
    public synchronized void insert(E o) {
        resequencer.insert(o);
    }
    
    public synchronized void deliver() throws Exception {
        resequencer.deliver();
    }
    
    public synchronized boolean deliverNext() throws Exception {
        return resequencer.deliverNext();
    }
}
