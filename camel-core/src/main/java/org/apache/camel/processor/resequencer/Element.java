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
 * A container for objects to be resequenced. This container can be scheduled
 * for timing out. Non-scheduled objects or already timed-out objects are ready
 * for being released by the {@link ResequencerEngine}.
 */
class Element<E> implements TimeoutHandler {

    /**
     * The contained object.
     */
    private E object;

    /**
     * Not <code>null</code> if this element is currently beeing scheduled for
     * timing out.
     */
    private Timeout timeout;
    
    /**
     * Creates a new container instance.
     * 
     * @param object contained object.
     */
    Element(E object) {
        this.object = object;
    }
    
    /**
     * Returns the contained object.
     * 
     * @return the contained object.
     */
    public E getObject() {
        return object;
    }

    /**
     * Returns <code>true</code> if this element is currently scheduled for
     * timing out.
     * 
     * @return <code>true</code> if scheduled or <code>false</code> if not
     *         scheduled or already timed-out.
     */
    public synchronized boolean scheduled() {
        return timeout != null;
    }
    
    /**
     * Schedules the given timeout task. Before this methods calls the
     * {@link Timeout#schedule()} method it sets this element as timeout
     * listener.
     * 
     * @param t a timeout task.
     */
    public synchronized void schedule(Timeout t) {
        this.timeout = t;
        this.timeout.setTimeoutHandler(this);
        this.timeout.schedule();
    }
    
    /**
     * Cancels the scheduled timeout for this element. If this element is not
     * scheduled or has already timed-out this method has no effect.
     */
    public synchronized void cancel() {
        if (timeout != null) {
            timeout.cancel();
        }
        timeout(null);
    }

    /**
     * Marks this element as timed-out.
     * 
     * @param t timeout task that caused the notification.
     */
    public synchronized void timeout(Timeout t) {
        this.timeout = null;
    }
    
}
