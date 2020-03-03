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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.processor.resequencer.ResequencerEngine;
import org.apache.camel.processor.resequencer.SequenceElementComparator;
import org.apache.camel.processor.resequencer.SequenceSender;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * successor of 3 is missing. The resequencer therefore has to retain message 5
 * until message 4 arrives (or a timeout occurs).
 * <p>
 * Instances of this class poll for {@link Exchange}s from a given
 * <code>endpoint</code>. Resequencing work and the delivery of messages to
 * the next <code>processor</code> is done within the single polling thread.
 *
 * @see ResequencerEngine
 */
public class StreamResequencer extends AsyncProcessorSupport implements SequenceSender<Exchange>, Navigate<Processor>, Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(StreamResequencer.class);

    private String id;
    private String routeId;
    private final CamelContext camelContext;
    private final ExceptionHandler exceptionHandler;
    private final ResequencerEngine<Exchange> engine;
    private final Processor processor;
    private final Expression expression;
    private Delivery delivery;
    private int capacity;
    private boolean ignoreInvalidExchanges;
    private long deliveryAttemptInterval = 1000L;

    /**
     * Creates a new {@link StreamResequencer} instance.
     * 
     * @param processor next processor that processes re-ordered exchanges.
     * @param comparator a sequence element comparator for exchanges.
     */
    public StreamResequencer(CamelContext camelContext, Processor processor, SequenceElementComparator<Exchange> comparator, Expression expression) {
        ObjectHelper.notNull(camelContext, "CamelContext");
        this.camelContext = camelContext;
        this.engine = new ResequencerEngine<>(comparator);
        this.engine.setSequenceSender(this);
        this.processor = processor;
        this.expression = expression;
        this.exceptionHandler = new LoggingExceptionHandler(camelContext, getClass());
    }

    public Expression getExpression() {
        return expression;
    }

    /**
     * Returns this resequencer's exception handler.
     */
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Returns the next processor.
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
     * @return this resequencer's timeout. (Processor)
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

    public void setDeliveryAttemptInterval(long deliveryAttemptInterval) {
        this.deliveryAttemptInterval = deliveryAttemptInterval;
    }

    public boolean isIgnoreInvalidExchanges() {
        return ignoreInvalidExchanges;
    }

    public void setRejectOld(Boolean rejectOld) {
        engine.setRejectOld(rejectOld);
    }

    public boolean isRejectOld() {
        return engine.getRejectOld() != null && engine.getRejectOld();
    }

    /**
     * Sets whether to ignore invalid exchanges which cannot be used by this stream resequencer.
     * <p/>
     * Default is <tt>false</tt>, by which an {@link CamelExchangeException} is thrown if the {@link Exchange}
     * is invalid.
     */
    public void setIgnoreInvalidExchanges(boolean ignoreInvalidExchanges) {
        this.ignoreInvalidExchanges = ignoreInvalidExchanges;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "streamResequence";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
        delivery = new Delivery();
        engine.start();
        delivery.start();
    }

    @Override
    protected void doStop() throws Exception {
        // let's stop everything in the reverse order
        // no need to stop the worker thread -- it will stop automatically when this service is stopped
        engine.stop();
        ServiceHelper.stopService(processor);
    }

    /**
     * Sends the <code>exchange</code> to the next <code>processor</code>.
     * 
     * @param exchange exchange to send.
     */
    @Override
    public void sendElement(Exchange exchange) throws Exception {
        processor.process(exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        while (engine.size() >= capacity) {
            try {
                Thread.sleep(getTimeout());
            } catch (InterruptedException e) {
                // we was interrupted so break out
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        try {
            engine.insert(exchange);
            delivery.request();
        } catch (Exception e) {
            if (isIgnoreInvalidExchanges()) {
                LOG.debug("Invalid Exchange. This Exchange will be ignored: {}", exchange);
            } else {
                exchange.setException(new CamelExchangeException("Error processing Exchange in StreamResequencer", exchange, e));
            }
        }

        callback.done(true);
        return true;
    }

    @Override
    public boolean hasNext() {
        return processor != null;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>(1);
        answer.add(processor);
        return answer;
    }

    class Delivery extends Thread {

        private Lock deliveryRequestLock = new ReentrantLock();
        private Condition deliveryRequestCondition = deliveryRequestLock.newCondition();
        
        Delivery() {
            super(camelContext.getExecutorServiceManager().resolveThreadName("Resequencer Delivery"));
        }
        
        @Override
        public void run() {
            while (isRunAllowed()) {
                try {
                    deliveryRequestLock.lock();
                    try {
                        deliveryRequestCondition.await(deliveryAttemptInterval, TimeUnit.MILLISECONDS);
                    } finally {
                        deliveryRequestLock.unlock();
                    }
                } catch (InterruptedException e) {
                    break;
                }
                try {
                    engine.deliver();
                } catch (Throwable t) {
                    // a fail safe to handle all exceptions being thrown
                    getExceptionHandler().handleException(t);
                }
            }
        }

        public void cancel() {
            interrupt();
        }
        
        public void request() {
            deliveryRequestLock.lock();
            try {
                deliveryRequestCondition.signal();
            } finally {
                deliveryRequestLock.unlock();
            }
        }
        
    }
    
}
