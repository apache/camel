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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.resequencer.ResequencerEngine;
import org.apache.camel.processor.resequencer.SequenceElementComparator;
import org.apache.camel.processor.resequencer.SequenceSender;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ServiceHelper;

/**
 * A resequencer that re-orders a (continuous) stream of {@link Exchange}s. The
 * algorithm implemented by {@link ResequencerEngine} is based on the detection
 * of gaps in a message stream rather than on a fixed batch size. Gap detection
 * in combination with timeouts removes the constraint of having to know the
 * number of messages of a sequence (i.e. the batch size) in advance.
 * <p>
 * Messages must contain a unique sequence number for which a predecessor and a
 * successor is known. For example a message with the sequence number 3 has a
 * predecessor message with the sequence number 2 and a successor message with
 * the sequence number 4. The message sequence 2,3,5 has a gap because the
 * sucessor of 3 is missing. The resequencer therefore has to retain message 5
 * until message 4 arrives (or a timeout occurs).
 * <p>
 * Instances of this class poll for {@link Exchange}s from a given
 * <code>endpoint</code>. Resequencing work and the delivery of messages to
 * the next <code>processor</code> is done within the single polling thread.
 * 
 * @author Martin Krasser
 * 
 * @version $Revision$
 * 
 * @see ResequencerEngine
 */
public class StreamResequencer extends ServiceSupport implements SequenceSender<Exchange>, Processor {

    private static final long DELIVERY_ATTEMPT_INTERVAL = 1000L;
    
    private ExceptionHandler exceptionHandler;
    private ResequencerEngine<Exchange> engine;
    private Processor processor;
    private Delivery delivery;
    private int capacity;
    
    /**
     * Creates a new {@link StreamResequencer} instance.
     * 
     * @param endpoint
     *            endpoint to poll exchanges from.
     * @param processor
     *            next processor that processes re-ordered exchanges.
     * @param comparator
     *            a sequence element comparator for exchanges.
     */
    public StreamResequencer(Processor processor, SequenceElementComparator<Exchange> comparator) {
        this.exceptionHandler = new LoggingExceptionHandler(getClass());
        this.engine = new ResequencerEngine<Exchange>(comparator);
        this.engine.setSequenceSender(this);
        this.processor = processor;
    }

    /**
     * Returns this resequencer's exception handler.
     * 
     * @return this resequencer's exception handler.
     */
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Returns the next processor.
     * 
     * @return the next processor.
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Returns this resequencer's capacity. The capacity is the maximum number
     * of exchanges that can be managed by this resequencer at a given point in
     * time. If the capacity if reached, polling from the endpoint will be
     * skipped for <code>timeout</code> milliseconds giving exchanges the
     * possibility to time out and to be delivered after the waiting period.
     * 
     * @return this resequencer's capacity.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Returns this resequencer's timeout. This sets the resequencer engine's
     * timeout via {@link ResequencerEngine#setTimeout(long)}. This value is
     * also used to define the polling timeout from the endpoint.
     * 
     * @return this resequencer's timeout.
     * (Processor) 
     * @see ResequencerEngine#setTimeout(long)
     */
    public long getTimeout() {
        return engine.getTimeout();
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void setTimeout(long timeout) {
        engine.setTimeout(timeout);
    }

    @Override
    public String toString() {
        return "StreamResequencer[to: " + processor + "]";
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
        delivery = new Delivery();
        engine.start();
        delivery.start();
    }

    @Override
    protected void doStop() throws Exception {
        // let's stop everything in the reverse order
        // no need to stop the worker thread -- it will stop automatically when this service is stopped
        engine.stop();
        ServiceHelper.stopServices(processor);
    }

    /**
     * Sends the <code>exchange</code> to the next <code>processor</code>.
     * 
     * @param o
     *            exchange to send.
     */
    public void sendElement(Exchange o) throws Exception {
        processor.process(o);
    }

    public void process(Exchange exchange) throws Exception {
        while (engine.size() >= capacity) {
            Thread.sleep(getTimeout());
        }
        engine.insert(exchange);
        delivery.request();
    }

    private class Delivery extends Thread {

        private volatile boolean cancelRequested;
        
        public Delivery() {
            super("Delivery Thread");
        }
        
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(DELIVERY_ATTEMPT_INTERVAL);
                } catch (InterruptedException e) {
                    if (cancelRequested) {
                        return;
                    }
                }
                try {
                    engine.deliver();
                } catch (Exception e) {
                    exceptionHandler.handleException(e);
                }
            }
        }

        public void cancel() {
            cancelRequested = true;
            interrupt();
        }
        
        public void request() {
            interrupt();
        }
        
    }
    
}