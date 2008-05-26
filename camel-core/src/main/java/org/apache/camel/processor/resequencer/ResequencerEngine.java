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

import java.util.Queue;
import java.util.Timer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Resequences elements based on a given {@link SequenceElementComparator}.
 * This resequencer is designed for resequencing element streams. Resequenced
 * elements are added to an output {@link Queue}. The resequencer is configured
 * via the <code>timeout</code> and <code>capacity</code> properties.
 * 
 * <ul>
 * <li><code>timeout</code>. Defines the timeout (in milliseconds) for a
 * given element managed by this resequencer. An out-of-sequence element can
 * only be marked as <i>ready-for-delivery</i> if it either times out or if it
 * has an immediate predecessor (in that case it is in-sequence). If an
 * immediate predecessor of a waiting element arrives the timeout task for the
 * waiting element will be cancelled (which marks it as <i>ready-for-delivery</i>).
 * <p>
 * If the maximum out-of-sequence time between elements within a stream is
 * known, the <code>timeout</code> value should be set to this value. In this
 * case it is guaranteed that all elements of a stream will be delivered in
 * sequence to the output queue. However, large <code>timeout</code> values
 * might require a very high resequencer <code>capacity</code> which might be
 * in conflict with available memory resources. The lower the
 * <code>timeout</code> value is compared to the out-of-sequence time between
 * elements within a stream the higher the probability is for out-of-sequence
 * elements delivered by this resequencer.</li>
 * <li><code>capacity</code>. The capacity of this resequencer.</li>
 * </ul>
 * 
 * Whenever a timeout for a certain element occurs or an element has been added
 * to this resequencer a delivery attempt is started. If a (sub)sequence of
 * elements is <i>ready-for-delivery</i> then they are added to output queue.
 * <p>
 * The resequencer remembers the last-delivered element. If an element arrives
 * which is the immediate successor of the last-delivered element it will be
 * delivered immediately and the last-delivered element is adjusted accordingly.
 * If the last-delivered element is <code>null</code> i.e. the resequencer was
 * newly created the first arriving element will wait <code>timeout</code>
 * milliseconds for being delivered to the output queue.
 * 
 * @author Martin Krasser
 * 
 * @version $Revision
 */
public class ResequencerEngine<E> implements TimeoutHandler {

    private static final transient Log LOG = LogFactory.getLog(ResequencerEngine.class);
    
    private long timeout;    
    private int capacity;    
    private Queue<E> outQueue;    
    private Element<E> lastDelivered;

    /**
     * A sequence of elements for sorting purposes.
     */
    private Sequence<Element<E>> sequence;
    
    /**
     * A timer for scheduling timeout notifications.
     */
    private Timer timer;
    
    /**
     * Creates a new resequencer instance with a default timeout of 2000
     * milliseconds. The capacity is set to {@link Integer#MAX_VALUE}.
     * 
     * @param comparator a sequence element comparator.
     */
    public ResequencerEngine(SequenceElementComparator<E> comparator) {
        this(comparator, Integer.MAX_VALUE);
    }

    /**
     * Creates a new resequencer instance with a default timeout of 2000
     * milliseconds.
     * 
     * @param comparator a sequence element comparator.
     * @param capacity the capacity of this resequencer.
     */
    public ResequencerEngine(SequenceElementComparator<E> comparator, int capacity) {
        this.timer = new Timer("Resequencer Timer");
        this.sequence = createSequence(comparator);
        this.capacity = capacity;
        this.timeout = 2000L;
        this.lastDelivered = null;
    }
    
    /**
     * Stops this resequencer (i.e. this resequencer's {@link Timer} instance).
     */
    public void stop() {
        this.timer.cancel();
    }
    
    /**
     * Returns the output queue.
     * 
     * @return the output queue.
     */
    public Queue<E> getOutQueue() {
        return outQueue;
    }

    /**
     * Sets the output queue.
     * 
     * @param outQueue output queue.
     */
    public void setOutQueue(Queue<E> outQueue) {
        this.outQueue = outQueue;
    }

    /**
     * Returns this resequencer's timeout value.
     * 
     * @return the timeout in milliseconds.
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets this sequencer's timeout value.
     * 
     * @param timeout the timeout in milliseconds.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /** 
     * Handles a timeout notification by starting a delivery attempt.
     * 
     * @param timout timeout task that caused the notification.
     */
    public synchronized void timeout(Timeout timout) {
        try {
            while (deliver()) {
                // work done in deliver()
            }
        } catch (RuntimeException e) {
            LOG.error("error during delivery", e);
        }
    }

    /**
     * Adds an element to this resequencer throwing an exception if the maximum
     * capacity is reached.
     * 
     * @param o element to be resequenced.
     * @throws IllegalStateException if the element cannot be added at this time
     *         due to capacity restrictions.
     */
    public synchronized void add(E o) {
        if (sequence.size() >= capacity) {
            throw new IllegalStateException("maximum capacity is reached");
        }
        insert(o);
    }
    
    /**
     * Adds an element to this resequencer waiting, if necessary, until capacity
     * becomes available.
     * 
     * @param o element to be resequenced.
     * @throws InterruptedException if interrupted while waiting.
     */
    public synchronized void put(E o) throws InterruptedException {
        if (sequence.size() >= capacity) {
            wait();
        }
        insert(o);
    }
    
    /**
     * Returns the last delivered element.
     * 
     * @return the last delivered element or <code>null</code> if no delivery
     *         has been made yet.
     */
    E getLastDelivered() {
        if (lastDelivered == null) {
            return null;
        }
        return lastDelivered.getObject();
    }
    
    /**
     * Sets the last delivered element. This is for testing purposes only.
     * 
     * @param o an element.
     */
    void setLastDelivered(E o) {
        lastDelivered = new Element<E>(o);
    }
    
    /**
     * Inserts the given element into this resequencing queue (sequence). If the
     * element is not ready for immediate delivery and has no immediate
     * presecessor then it is scheduled for timing out. After being timed out it
     * is ready for delivery.
     * 
     * @param o an element.
     */
    private void insert(E o) {
        // wrap object into internal element
        Element<E> element = new Element<E>(o);
        // add element to sequence in proper order
        sequence.add(element);

        Element<E> successor = sequence.successor(element);
        
        // check if there is an immediate successor and cancel
        // timer task (no need to wait any more for timeout)
        if (successor != null) {
            successor.cancel();
        }
        
        // start delivery if current element is successor of last delivered element
        if (successorOfLastDelivered(element)) {
            // nothing to schedule
        } else if (sequence.predecessor(element) != null) {
            // nothing to schedule
        } else {
            Timeout t = defineTimeout();
            element.schedule(t);
        }
        
        // start delivery
        while (deliver()) {
            // work done in deliver()
        }
    }
    
    /**
     * Attempts to deliver a single element from the head of the resequencer
     * queue (sequence). Only elements which have not been scheduled for timing
     * out or which already timed out can be delivered.
     * 
     * @return <code>true</code> if the element has been delivered
     *         <code>false</code> otherwise.
     */
    private boolean deliver() {
        if (sequence.size() == 0) {
            return false;
        }
        // inspect element with lowest sequence value
        Element<E> element = sequence.first();
        
        // if element is scheduled do not deliver and return
        if (element.scheduled()) {
            return false;
        }
        
        // remove deliverable element from sequence
        sequence.remove(element);

        // set the delivered element to last delivered element
        lastDelivered = element;
        
        // notify a waiting thread that capacity is available
        notify();
        
        // add element to output queue
        outQueue.add(element.getObject());

        // element has been delivered
        return true;
    }
    
    /**
     * Returns <code>true</code> if the given element is the immediate
     * successor of the last delivered element.
     * 
     * @param element an element.
     * @return <code>true</code> if the given element is the immediate
     *         successor of the last delivered element.
     */
    private boolean successorOfLastDelivered(Element<E> element) {
        if (lastDelivered == null) {
            return false;
        }
        if (sequence.comparator().successor(element, lastDelivered)) {
            return true;
        }
        return false;
    }
    
    /**
     * Creates a timeout task based on the timeout setting of this resequencer.
     * 
     * @return a new timeout task.
     */
    private Timeout defineTimeout() {
        Timeout result = new Timeout(timer, timeout);
        result.addTimeoutHandler(this);
        return result;
    }
    
    private static <E> Sequence<Element<E>> createSequence(SequenceElementComparator<E> comparator) {
        return new Sequence<Element<E>>(new ElementComparator<E>(comparator));
    }
    
}
