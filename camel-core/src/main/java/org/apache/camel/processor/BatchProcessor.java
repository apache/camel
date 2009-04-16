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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ServiceHelper;

/**
 * A base class for any kind of {@link Processor} which implements some kind of batch processing.
 * 
 * @version $Revision$
 */
public class BatchProcessor extends ServiceSupport implements Processor {

    public static final long DEFAULT_BATCH_TIMEOUT = 1000L;
    public static final int DEFAULT_BATCH_SIZE = 100;

    private long batchTimeout = DEFAULT_BATCH_TIMEOUT;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private int outBatchSize;

    private Processor processor;
    private Collection<Exchange> collection;
    private ExceptionHandler exceptionHandler;

    private BatchSender sender;
    
    public BatchProcessor(Processor processor, Collection<Exchange> collection) {
        this.processor = processor;
        this.collection = collection;
        this.sender = new BatchSender();
    }

    @Override
    public String toString() {
        return "BatchProcessor[to: " + processor + "]";
    }

    // Properties
    // -------------------------------------------------------------------------
    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the <b>in</b> batch size. This is the number of incoming exchanges that this batch processor will
     * process before its completed. The default value is {@link #DEFAULT_BATCH_SIZE}.
     * 
     * @param batchSize the size
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getOutBatchSize() {
        return outBatchSize;
    }

    /**
     * Sets the <b>out</b> batch size. If the batch processor holds more exchanges than this out size then the
     * completion is triggered. Can for instance be used to ensure that this batch is completed when a certain
     * number of exchanges has been collected. By default this feature is <b>not</b> enabled.
     * 
     * @param outBatchSize the size
     */
    public void setOutBatchSize(int outBatchSize) {
        this.outBatchSize = outBatchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public Processor getProcessor() {
        return processor;
    }

    /**
     * A strategy method to decide if the "in" batch is completed. That is, whether the resulting exchanges in
     * the in queue should be drained to the "out" collection.
     */
    private boolean isInBatchCompleted(int num) {
        return num >= batchSize;
    }

    /**
     * A strategy method to decide if the "out" batch is completed. That is, whether the resulting exchange in
     * the out collection should be sent.
     */
    private boolean isOutBatchCompleted() {
        if (outBatchSize == 0) {
            // out batch is disabled, so go ahead and send.
            return true;
        }
        return collection.size() > 0 && collection.size() >= outBatchSize;
    }

    /**
     * Strategy Method to process an exchange in the batch. This method allows derived classes to perform
     * custom processing before or after an individual exchange is processed
     */
    protected void processExchange(Exchange exchange) throws Exception {
        processor.process(exchange);
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processor);
        sender.start();
    }

    protected void doStop() throws Exception {
        sender.cancel();
        ServiceHelper.stopServices(processor);
        collection.clear();
    }

    /**
     * Enqueues an exchange for later batch processing.
     */
    public void process(Exchange exchange) throws Exception {
        sender.enqueueExchange(exchange);
    }

    /**
     * Sender thread for queued-up exchanges.
     */
    private class BatchSender extends Thread {

        private Queue<Exchange> queue;
        private Lock queueLock = new ReentrantLock();
        private boolean exchangeEnqueued;
        private Condition exchangeEnqueuedCondition = queueLock.newCondition();

        public BatchSender() {
            super("Batch Sender");
            this.queue = new LinkedList<Exchange>();
        }

        @Override
        public void run() {
            // Wait until one of either:
            // * an exchange being queued;
            // * the batch timeout expiring; or
            // * the thread being cancelled.
            //
            // If an exchange is queued then we need to determine whether the
            // batch is complete. If it is complete then we send out the batched
            // exchanges. Otherwise we move back into our wait state.
            //
            // If the batch times out then we send out the batched exchanges
            // collected so far.
            //
            // If we receive an interrupt then all blocking operations are
            // interrupted and our thread terminates.
            //
            // The goal of the following algorithm in terms of synchronisation
            // is to provide fine grained locking i.e. retaining the lock only
            // when required. Special consideration is given to releasing the
            // lock when calling an overloaded method such as isInBatchComplete,
            // isOutBatchComplete and around sendExchanges. The latter is
            // especially important as the process of sending out the exchanges
            // would otherwise block new exchanges from being queued.

            queueLock.lock();
            try {
                do {
                    try {
                        if (!exchangeEnqueued) {
                            exchangeEnqueuedCondition.await(batchTimeout, TimeUnit.MILLISECONDS);
                        }

                        if (!exchangeEnqueued) {
                            drainQueueTo(collection, batchSize);
                        } else {             
                            exchangeEnqueued = false;
                            while (isInBatchCompleted(queue.size())) {   
                                drainQueueTo(collection, batchSize);
                            }
                            
                            queueLock.unlock();
                            try {
                                if (!isOutBatchCompleted()) {
                                    continue;
                                }
                            } finally {
                                queueLock.lock();
                            }
                        }

                        queueLock.unlock();
                        try {
                            try {
                                sendExchanges();
                            } catch (Exception e) {
                                getExceptionHandler().handleException(e);
                            }
                        } finally {
                            queueLock.lock();
                        }

                    } catch (InterruptedException e) {
                        break;
                    }

                } while (true);

            } finally {
                queueLock.unlock();
            }
        }

        /**
         * This method should be called with queueLock held
         */
        private void drainQueueTo(Collection<Exchange> collection, int batchSize) {
            for (int i = 0; i < batchSize; ++i) {
                Exchange e = queue.poll();
                if (e != null) {
                    collection.add(e);
                } else {
                    break;
                }
            }
        }

        public void cancel() {
            interrupt();
        }

        public void enqueueExchange(Exchange exchange) {
            queueLock.lock();
            try {
                queue.add(exchange);
                exchangeEnqueued = true;
                exchangeEnqueuedCondition.signal();
            } finally {
                queueLock.unlock();
            }
        }

        private void sendExchanges() throws Exception {
            Iterator<Exchange> iter = collection.iterator();
            while (iter.hasNext()) {
                Exchange exchange = iter.next();
                iter.remove();
                processExchange(exchange);
            }
        }
    }

}
