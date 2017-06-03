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
package org.apache.camel.processor.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default base class for a {@link LoadBalancer} implementation.
 * <p/>
 * This implementation is dedicated for asynchronous load balancers.
 * <p/>
 * Consider using the {@link SimpleLoadBalancerSupport} if your load balancer does not by nature
 * support asynchronous routing.
 *
 * @version 
 */
public abstract class LoadBalancerSupport extends ServiceSupport implements LoadBalancer, Navigate<Processor>, IdAware {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final List<Processor> processors = new CopyOnWriteArrayList<Processor>();
    private String id;

    public void addProcessor(Processor processor) {
        processors.add(processor);
    }

    public void removeProcessor(Processor processor) {
        processors.remove(processor);
    }

    public List<Processor> getProcessors() {
        return processors;
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        return new ArrayList<Processor>(processors);
    }

    public boolean hasNext() {
        return processors.size() > 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processors);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processors);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processors);
        for (Processor processor : processors) {
            removeProcessor(processor);
        }
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }
}
