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
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ServiceHelper;

/**
 * A base class for any kind of {@link Processor} which implements some kind of
 * batch processing.
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
     * Sets the <b>in</b> batch size. This is the number of incoming exchanges that this batch processor
     * will process before its completed. The default value is {@link #DEFAULT_BATCH_SIZE}.
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
     * Sets the <b>out</b> batch size. If the batch processor holds more exchanges than this out size then
     * the completion is triggered. Can for instance be used to ensure that this batch is completed when
     * a certain number of exchanges has been collected. By default this feature is <b>not</b> enabled.
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
     * A strategy method to decide if the "in" batch is completed.  That is, whether the resulting 
     * exchanges in the in queue should be drained to the "out" collection.
     */
    protected boolean isInBatchCompleted(int num) {
        return num >= batchSize;
    }
    
    /**
     * A strategy method to decide if the "out" batch is completed. That is, whether the resulting 
     * exchange in the out collection should be sent.
     */
    protected boolean isOutBatchCompleted() {
        if (outBatchSize == 0) {
            // out batch is disabled, so go ahead and send.
            return true;
        }
        return collection.size() > 0 && collection.size() >= outBatchSize;
    }

    /**
     * Strategy Method to process an exchange in the batch. This method allows
     * derived classes to perform custom processing before or after an
     * individual exchange is processed
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

    protected Collection<Exchange> getCollection() {
        return collection;
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
        
        private volatile boolean cancelRequested;

        private LinkedBlockingQueue<Exchange> queue;
        
        public BatchSender() {
            super("Batch Sender");
            this.queue = new LinkedBlockingQueue<Exchange>();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(batchTimeout);
                    queue.drainTo(collection, batchSize);  
                } catch (InterruptedException e) {
                    if (cancelRequested) {
                        return;
                    }
                    
                    while (isInBatchCompleted(queue.size())) {
                        queue.drainTo(collection, batchSize);  
                    }
                    
                    if (!isOutBatchCompleted()) {
                        continue;
                    }
                }
                try {
                    sendExchanges();
                } catch (Exception e) {
                    getExceptionHandler().handleException(e);
                }
            }
        }
        
        public void cancel() {
            cancelRequested = true;
            interrupt();
        }
     
        public void enqueueExchange(Exchange exchange) {
            queue.add(exchange);
            interrupt();
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
