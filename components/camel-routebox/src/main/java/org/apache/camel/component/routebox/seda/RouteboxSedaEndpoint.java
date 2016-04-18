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
package org.apache.camel.component.routebox.seda;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.component.routebox.RouteboxComponent;
import org.apache.camel.component.routebox.RouteboxConfiguration;
import org.apache.camel.component.routebox.RouteboxConsumer;
import org.apache.camel.component.routebox.RouteboxEndpoint;
import org.apache.camel.component.routebox.RouteboxProducer;
import org.apache.camel.spi.BrowsableEndpoint;

public class RouteboxSedaEndpoint extends RouteboxEndpoint implements AsyncEndpoint, BrowsableEndpoint, MultipleConsumersSupport {
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;
    private volatile BlockingQueue<Exchange> queue;
    private volatile Set<RouteboxProducer> producers = new CopyOnWriteArraySet<RouteboxProducer>();
    private volatile Set<RouteboxConsumer> consumers = new CopyOnWriteArraySet<RouteboxConsumer>();

    public RouteboxSedaEndpoint(String endpointUri, RouteboxComponent component, RouteboxConfiguration config) throws Exception {
        super(endpointUri, component, config);
    }

    public RouteboxSedaEndpoint(String endpointUri, RouteboxComponent component, RouteboxConfiguration config, BlockingQueue<Exchange> queue) throws Exception {
        this(endpointUri, component, config);
        this.queue = queue;
    }
    
    public Consumer createConsumer(Processor processor) throws Exception {
        RouteboxSedaConsumer answer = new RouteboxSedaConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        return new RouteboxSedaProducer(this, queue, getWaitForTaskToComplete(), getConfig().getConnectionTimeout());
    }

    public boolean isSingleton() {
        return true;
    }

    public void onStarted(RouteboxProducer producer) {
        producers.add(producer);
    }

    public void onStopped(RouteboxProducer producer) {
        producers.remove(producer);
    }

    public void onStarted(RouteboxConsumer consumer) {
        consumers.add(consumer);
    }

    public void onStopped(RouteboxConsumer consumer) {
        consumers.remove(consumer);
    }

    public Set<RouteboxConsumer> getConsumers() {
        return new HashSet<RouteboxConsumer>(consumers);
    }

    public Set<RouteboxProducer> getProducers() {
        return new HashSet<RouteboxProducer>(producers);
    }

    public void setQueue(BlockingQueue<Exchange> queue) {
        this.queue = queue;
    }

    public WaitForTaskToComplete getWaitForTaskToComplete() {
        return waitForTaskToComplete;
    }

    public void setWaitForTaskToComplete(WaitForTaskToComplete waitForTaskToComplete) {
        this.waitForTaskToComplete = waitForTaskToComplete;
    }

    public BlockingQueue<Exchange> getQueue() {
        if (queue == null) {
            if (getConfig().getQueueSize() > 0) {
                queue = new LinkedBlockingQueue<Exchange>(getConfig().getQueueSize());
            } else {
                queue = new LinkedBlockingQueue<Exchange>();
            }
        }
        return queue;
    }

    public boolean isMultipleConsumersSupported() {
        return true;
    }

    public List<Exchange> getExchanges() {
        return new ArrayList<Exchange>(getQueue());
    }

}
