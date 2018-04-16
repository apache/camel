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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;

/**
 * Implements a sticky load balancer using an {@link Expression} to calculate
 * a correlation key to perform the sticky load balancing; rather like jsessionid in the web
 * or JMSXGroupID in JMS.
 *
 * @version 
 */
public class StickyLoadBalancer extends QueueLoadBalancer {
    private Expression correlationExpression;
    private RoundRobinLoadBalancer loadBalancer;
    private int numberOfHashGroups = 64 * 1024;
    private final Map<Object, Processor> stickyMap = new HashMap<>();

    public StickyLoadBalancer(Expression correlationExpression) {
        this.correlationExpression = correlationExpression;
        this.loadBalancer = new RoundRobinLoadBalancer();
    }

    protected synchronized Processor chooseProcessor(List<Processor> processors, Exchange exchange) {
        Object value = correlationExpression.evaluate(exchange, Object.class);
        Object key = getStickyKey(value);

        Processor processor;
        synchronized (stickyMap) {
            processor = stickyMap.get(key);
            if (processor == null) {
                processor = loadBalancer.chooseProcessor(processors, exchange);
                stickyMap.put(key, processor);
            }
        }
        return processor;
    }

    @Override
    public void removeProcessor(Processor processor) {
        synchronized (stickyMap) {
            Iterator<Map.Entry<Object, Processor>> iter = stickyMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Object, Processor> entry = iter.next();
                if (processor.equals(entry.getValue())) {
                    iter.remove();
                }
            }
        }
        super.removeProcessor(processor);
    }

    public int getLastChosenProcessorIndex() {
        return loadBalancer.getLastChosenProcessorIndex();
    }

    public Expression getCorrelationExpression() {
        return correlationExpression;
    }

    // Properties
    //-------------------------------------------------------------------------

    public int getNumberOfHashGroups() {
        return numberOfHashGroups;
    }

    public void setNumberOfHashGroups(int numberOfHashGroups) {
        this.numberOfHashGroups = numberOfHashGroups;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * A strategy to create the key for the sticky load balancing map.
     * The default implementation uses the hash code of the value
     * then modulos by the numberOfHashGroups to avoid the sticky map getting too big
     *
     * @param value the correlation value
     * @return the key to be used in the sticky map
     */
    protected Object getStickyKey(Object value) {
        int hashCode = 37;
        if (value != null) {
            hashCode = value.hashCode();
        }
        if (numberOfHashGroups > 0) {
            hashCode = hashCode % numberOfHashGroups;
        }
        return hashCode;
    }

    public String toString() {
        return "StickyLoadBalancer";
    }

}
