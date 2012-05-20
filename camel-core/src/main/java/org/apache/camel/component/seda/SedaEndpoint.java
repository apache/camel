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
import org.apache.camel.Message;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;

/**
 * An implementation of the <a
 * href="http://camel.apache.org/queue.html">Queue components</a> for
 * asynchronous SEDA exchanges on a {@link BlockingQueue} within a CamelContext
 */
@ManagedResource(description = "Managed SedaEndpoint")
public class SedaEndpoint extends DefaultEndpoint implements BrowsableEndpoint, MultipleConsumersSupport {
    private volatile BlockingQueue<Exchange> queue;
    private int size;
    private int concurrentConsumers = 1;
    private volatile ExecutorService multicastExecutor;
    private boolean multipleConsumers;
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;
    private long timeout = 30000;
    private final Set<SedaProducer> producers = new CopyOnWriteArraySet<SedaProducer>();
    private final Set<SedaConsumer> consumers = new CopyOnWriteArraySet<SedaConsumer>();
    private volatile MulticastProcessor consumerMulticastProcessor;
    private volatile boolean multicastStarted;
    private boolean blockWhenFull;
    private int pollTimeout = 1000;

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

    @Override
    public SedaComponent getComponent() {
        return (SedaComponent) super.getComponent();
    }

    public Producer createProducer() throws Exception {
        return new SedaProducer(this, getQueue(), getWaitForTaskToComplete(), getTimeout(), isBlockWhenFull());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new SedaConsumer(this, processor);
    }

    public synchronized BlockingQueue<Exchange> getQueue() {
        if (queue == null) {
            // prefer to lookup queue from component, so if this endpoint is re-created or re-started
            // then the existing queue from the component can be used, so new producers and consumers
            // can use the already existing queue referenced from the component
            if (getComponent() != null) {
                queue = getComponent().getOrCreateQueue(getEndpointUri(), getSize());
            } else {
                // fallback and create queue (as this endpoint has no component)
                queue = createQueue();
            }
        }
        return queue;
    }

    protected BlockingQueue<Exchange> createQueue() {
        if (size > 0) {
            return new LinkedBlockingQueue<Exchange>(size);
        } else {
            return new LinkedBlockingQueue<Exchange>();
        }
    }

    protected synchronized MulticastProcessor getConsumerMulticastProcessor() throws Exception {
        if (!multicastStarted && consumerMulticastProcessor != null) {
            // only start it on-demand to avoid starting it during stopping
            ServiceHelper.startService(consumerMulticastProcessor);
            multicastStarted = true;
        }
        return consumerMulticastProcessor;
    }

    protected synchronized void updateMulticastProcessor() throws Exception {
        if (consumerMulticastProcessor != null) {
            ServiceHelper.stopService(consumerMulticastProcessor);
        }

        int size = getConsumers().size();
        if (size == 0 && multicastExecutor != null) {
            // stop the multicast executor as its not needed anymore when size is zero
            getCamelContext().getExecutorServiceManager().shutdown(multicastExecutor);
            multicastExecutor = null;
        }
        if (size > 1) {
            if (multicastExecutor == null) {
                // create multicast executor as we need it when we have more than 1 processor
                multicastExecutor = getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, URISupport.sanitizeUri(getEndpointUri()) + "(multicast)");
            }
            // create list of consumers to multicast to
            List<Processor> processors = new ArrayList<Processor>(size);
            for (SedaConsumer consumer : getConsumers()) {
                processors.add(consumer.getProcessor());
            }
            // create multicast processor
            multicastStarted = false;
            consumerMulticastProcessor = new MulticastProcessor(getCamelContext(), processors, null, true, multicastExecutor, false, false, false, 0, null, false);
        } else {
            // not needed
            consumerMulticastProcessor = null;
        }
    }

    public void setQueue(BlockingQueue<Exchange> queue) {
        this.queue = queue;
        this.size = queue.remainingCapacity();
    }

    @ManagedAttribute(description = "Queue max capacity")
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @ManagedAttribute(description = "Current queue size")
    public int getCurrentQueueSize() {
        return queue.size();
    }

    public void setBlockWhenFull(boolean blockWhenFull) {
        this.blockWhenFull = blockWhenFull;
    }

    @ManagedAttribute(description = "Whether the caller will block sending to a full queue")
    public boolean isBlockWhenFull() {
        return blockWhenFull;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    @ManagedAttribute(description = "Number of concurrent consumers")
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public WaitForTaskToComplete getWaitForTaskToComplete() {
        return waitForTaskToComplete;
    }

    public void setWaitForTaskToComplete(WaitForTaskToComplete waitForTaskToComplete) {
        this.waitForTaskToComplete = waitForTaskToComplete;
    }

    @ManagedAttribute
    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @ManagedAttribute
    public boolean isMultipleConsumers() {
        return multipleConsumers;
    }

    public void setMultipleConsumers(boolean multipleConsumers) {
        this.multipleConsumers = multipleConsumers;
    }

    @ManagedAttribute
    public int getPollTimeout() {
        return pollTimeout;
    }

    public void setPollTimeout(int pollTimeout) {
        this.pollTimeout = pollTimeout;
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

    @ManagedAttribute
    public boolean isMultipleConsumersSupported() {
        return isMultipleConsumers();
    }

    /**
     * Purges the queue
     */
    @ManagedOperation(description = "Purges the seda queue")
    public void purgeQueue() {
        queue.clear();
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

    @ManagedOperation(description = "Current number of Exchanges in Queue")
    public long queueSize() {
        return getExchanges().size();
    }

    @ManagedOperation(description = "Get Exchange from queue by index")
    public String browseExchange(Integer index) {
        List<Exchange> exchanges = getExchanges();
        if (index >= exchanges.size()) {
            return null;
        }
        Exchange exchange = exchanges.get(index);
        if (exchange == null) {
            return null;
        }
        // must use java type with JMX such as java.lang.String
        return exchange.toString();
    }

    @ManagedOperation(description = "Get message body from queue by index")
    public String browseMessageBody(Integer index) {
        List<Exchange> exchanges = getExchanges();
        if (index >= exchanges.size()) {
            return null;
        }
        Exchange exchange = exchanges.get(index);
        if (exchange == null) {
            return null;
        }

        // must use java type with JMX such as java.lang.String
        String body;
        if (exchange.hasOut()) {
            body = exchange.getOut().getBody(String.class);
        } else {
            body = exchange.getIn().getBody(String.class);
        }

        return body;
    }

    @ManagedOperation(description = "Get message as XML from queue by index")
    public String browseMessageAsXml(Integer index, Boolean includeBody) {
        List<Exchange> exchanges = getExchanges();
        if (index >= exchanges.size()) {
            return null;
        }
        Exchange exchange = exchanges.get(index);
        if (exchange == null) {
            return null;
        }

        Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        String xml = MessageHelper.dumpAsXml(msg, includeBody);

        return xml;
    }

    @ManagedOperation(description = "Gets all the messages as XML from the queue")
    public String browseAllMessagesAsXml(Boolean includeBody) {
        return browseRangeMessagesAsXml(0, Integer.MAX_VALUE, includeBody);
    }

    @ManagedOperation(description = "Gets the range of messages as XML from the queue")
    public String browseRangeMessagesAsXml(Integer fromIndex, Integer toIndex, Boolean includeBody) {
        return EndpointHelper.browseRangeMessagesAsXml(this, fromIndex, toIndex, includeBody);
    }

    void onStarted(SedaProducer producer) {
        producers.add(producer);
    }

    void onStopped(SedaProducer producer) {
        producers.remove(producer);
    }

    void onStarted(SedaConsumer consumer) throws Exception {
        consumers.add(consumer);
        if (isMultipleConsumers()) {
            updateMulticastProcessor();
        }
    }

    void onStopped(SedaConsumer consumer) throws Exception {
        consumers.remove(consumer);
        if (isMultipleConsumers()) {
            updateMulticastProcessor();
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // special for unit testing where we can set a system property to make seda poll faster
        // and therefore also react faster upon shutdown, which makes overall testing faster of the Camel project
        String override = System.getProperty("CamelSedaPollTimeout", "" + getPollTimeout());
        setPollTimeout(Integer.valueOf(override));
    }

    @Override
    protected void doShutdown() throws Exception {
        // notify component we are shutting down this endpoint
        if (getComponent() != null) {
            getComponent().onShutdownEndpoint(this);
        }
        // shutdown thread pool if it was in use
        if (multicastExecutor != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(multicastExecutor);
            multicastExecutor = null;
        }

        // clear queue, as we are shutdown, so if re-created then the queue must be updated
        queue = null;

        super.doShutdown();
    }
}
