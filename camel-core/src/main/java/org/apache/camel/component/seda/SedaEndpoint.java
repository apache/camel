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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.spi.BrowsableEndpoint;

/**
 * An implementation of the <a
 * href="http://camel.apache.org/queue.html">Queue components</a> for
 * asynchronous SEDA exchanges on a {@link BlockingQueue} within a CamelContext
 *
 * @version $Revision$
 */
public class SedaEndpoint extends DefaultEndpoint implements BrowsableEndpoint, MultipleConsumersSupport {
    private volatile BlockingQueue<Exchange> queue;
    private int size;
    private int concurrentConsumers = 1;
    private volatile ExecutorService multicastExecutor;
    private boolean multipleConsumers;
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;
    private long timeout = 30000;
    private volatile Set<SedaProducer> producers = new CopyOnWriteArraySet<SedaProducer>();
    private volatile Set<SedaConsumer> consumers = new CopyOnWriteArraySet<SedaConsumer>();
    private volatile MulticastProcessor conumserMulticastProcessor;

    public SedaEndpoint() {
    }

    public SedaEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue) {
        this(endpointUri, component, queue, 1);
    }

    public SedaEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        super(endpointUri, component);
        this.queue = queue;
        this.size = queue.remainingCapacity();
        this.concurrentConsumers = concurrentConsumers;
    }

    public SedaEndpoint(String endpointUri, BlockingQueue<Exchange> queue) {
        this(endpointUri, queue, 1);
    }

    public SedaEndpoint(String endpointUri, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        super(endpointUri);
        this.queue = queue;
        this.size = queue.remainingCapacity();
        this.concurrentConsumers = concurrentConsumers;
    }
    
    public Producer createProducer() throws Exception {
        return new SedaProducer(this, getQueue(), getWaitForTaskToComplete(), getTimeout());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new SedaConsumer(this, processor);
    }

    public synchronized BlockingQueue<Exchange> getQueue() {
        if (queue == null) {
            if (size > 0) {
                queue = new LinkedBlockingQueue<Exchange>(size);
            } else {
                queue = new LinkedBlockingQueue<Exchange>();
            }
        }
        return queue;
    }
    
    protected synchronized MulticastProcessor getConumserMulticastProcessor() {
        return conumserMulticastProcessor;
    }
    
    protected synchronized void updateMulticastProcessor() {
        int size = getConsumers().size();
        if (size == 0 && multicastExecutor != null) {
            // stop the multicastExecutor
            getCamelContext().getExecutorServiceStrategy().shutdown(multicastExecutor);
            multicastExecutor = null;
        }
        if (size == 1 && multicastExecutor == null) {
            multicastExecutor = getCamelContext().getExecutorServiceStrategy().newDefaultThreadPool(this, getEndpointUri() + "(multicast)");
        }
        List<Processor> processors = new ArrayList<Processor>(size);
        for (SedaConsumer consumer : getConsumers()) {
            processors.add(consumer.getProcessor());
        }
        conumserMulticastProcessor = new MulticastProcessor(getCamelContext(), processors, null, true, multicastExecutor, false, false, 0);
   
    }

    public void setQueue(BlockingQueue<Exchange> queue) {
        this.queue = queue;
        this.size = queue.remainingCapacity();
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }
    
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public WaitForTaskToComplete getWaitForTaskToComplete() {
        return waitForTaskToComplete;
    }

    public void setWaitForTaskToComplete(WaitForTaskToComplete waitForTaskToComplete) {
        this.waitForTaskToComplete = waitForTaskToComplete;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isMultipleConsumers() {
        return multipleConsumers;
    }

    public void setMultipleConsumers(boolean multipleConsumers) {
        this.multipleConsumers = multipleConsumers;
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Returns the current pending exchanges
     */
    public List<Exchange> getExchanges() {
        return new ArrayList<Exchange>(getQueue());
    }

    public boolean isMultipleConsumersSupported() {
        return isMultipleConsumers();
    }

    /**
     * Returns the current active consumers on this endpoint
     */
    public Set<SedaConsumer> getConsumers() {
        return new HashSet<SedaConsumer>(consumers);
    }

    /**
     * Returns the current active producers on this endpoint
     */
    public Set<SedaProducer> getProducers() {
        return new HashSet<SedaProducer>(producers);
    }
    
    void onStarted(SedaProducer producer) {
        producers.add(producer);
    }

    void onStopped(SedaProducer producer) {
        producers.remove(producer);
    }

    void onStarted(SedaConsumer consumer) {
        consumers.add(consumer);
        updateMulticastProcessor();
    }

    void onStopped(SedaConsumer consumer) {
        consumers.remove(consumer);
        updateMulticastProcessor();
    }

}
