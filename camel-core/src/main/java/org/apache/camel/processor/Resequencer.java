/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.ExpressionComparator;
import org.apache.camel.util.ExpressionListComparator;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * An implementation of the <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a>
 *
 * @version $Revision: 1.1 $
 */
public class Resequencer extends ServiceSupport implements Runnable {
    private static final transient Log log = LogFactory.getLog(Resequencer.class);
    private Endpoint endpoint;
    private Processor processor;
    private Set<Exchange> set;
    private long batchTimeout = 1000L;
    private int batchSize = 100;
    private PollingConsumer consumer;
    private ExceptionHandler exceptionHandler;

    public Resequencer(Endpoint endpoint, Processor processor, Expression<Exchange> expression) {
        this(endpoint, processor, createSet(expression));
    }

    public Resequencer(Endpoint endpoint, Processor processor, List<Expression<Exchange>> expressions) {
        this(endpoint, processor, createSet(expressions));
    }

    public Resequencer(Endpoint endpoint, Processor processor, Set<Exchange> set) {
        this.endpoint = endpoint;
        this.processor = processor;
        this.set = set;
    }

    @Override
    public String toString() {
        return "Resequencer[to: " + processor + "]";
    }

    public void run() {
        log.debug("Starting thread for " + this);
        while (!isStopped() && !isStopping()) {
            try {
                processBatch();
            }
            catch (Exception e) {
                getExceptionHandler().handleException(e);
            }
        }
        set.clear();
    }

    // Properties
    //-------------------------------------------------------------------------
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

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * A transactional method to process a batch of messages up to a timeout period
     * or number of messages reached.
     */
    protected synchronized void processBatch() throws Exception {
        long start = System.currentTimeMillis();
        long end = start + batchTimeout;
        for (int i = 0; i < batchSize; i++) {
            long timeout = end - System.currentTimeMillis();

            Exchange exchange = consumer.receive(timeout);
            if (exchange == null) {
                break;
            }
            set.add(exchange);
        }

        if (log.isDebugEnabled()) {
            log.debug("Finsihed batch size: " + batchSize + " timeout: " + batchTimeout + " so sending set: " + set);
        }

        // lets send the batch
        Iterator<Exchange> iter = set.iterator();
        while (iter.hasNext()) {
            Exchange exchange = iter.next();
            iter.remove();
            processor.process(exchange);
        }
    }

    protected void doStart() throws Exception {
        consumer = endpoint.createPollingConsumer();

        ServiceHelper.startServices(processor, consumer);

        Thread thread = new Thread(this, this + " Polling Thread");
        thread.start();
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(consumer, processor);
        consumer = null;
    }

    protected static Set<Exchange> createSet(Expression<Exchange> expression) {
        return createSet(new ExpressionComparator<Exchange>(expression));
    }

    protected static Set<Exchange> createSet(List<Expression<Exchange>> expressions) {
        if (expressions.size() == 1) {
            return createSet(expressions.get(0));
        }
        return createSet(new ExpressionListComparator<Exchange>(expressions));
    }

    protected static Set<Exchange> createSet(Comparator<? super Exchange> comparator) {
        return new TreeSet<Exchange>(comparator);
    }
}
