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
package org.apache.camel.management.mbean;

import java.util.Collection;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedExchangeFactoryManagerMBean;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed ExchangeFactory")
public class ManagedExchangeFactoryManager extends ManagedService implements ManagedExchangeFactoryManagerMBean {

    private final ExchangeFactoryManager exchangeFactoryManager;
    private boolean sanitize;

    public ManagedExchangeFactoryManager(CamelContext context, ExchangeFactoryManager exchangeFactoryManager) {
        super(context, exchangeFactoryManager);
        this.exchangeFactoryManager = exchangeFactoryManager;
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : false;
    }

    @Override
    public Integer getConsumerCounter() {
        return exchangeFactoryManager.getConsumerCounter();
    }

    @Override
    public Integer getTotalPooled() {
        return exchangeFactoryManager.getPooledCounter();
    }

    @Override
    public Integer getCapacity() {
        return exchangeFactoryManager.getCapacity();
    }

    @Override
    public Boolean getStatisticsEnabled() {
        return exchangeFactoryManager.isStatisticsEnabled();
    }

    @Override
    public void setStatisticsEnabled(Boolean statisticsEnabled) {
        exchangeFactoryManager.setStatisticsEnabled(statisticsEnabled);
    }

    @Override
    public void resetStatistics() {
        exchangeFactoryManager.resetStatistics();
    }

    @Override
    public void purge() {
        exchangeFactoryManager.purge();
    }

    @Override
    public Long getTotalCreated() {
        return exchangeFactoryManager.getStatistics().getCreatedCounter();
    }

    @Override
    public Long getTotalAcquired() {
        return exchangeFactoryManager.getStatistics().getAcquiredCounter();
    }

    @Override
    public Long getTotalReleased() {
        return exchangeFactoryManager.getStatistics().getReleasedCounter();
    }

    @Override
    public Long getTotalDiscarded() {
        return exchangeFactoryManager.getStatistics().getDiscardedCounter();
    }

    @Override
    public TabularData listStatistics() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listExchangeFactoryTabularType());
            Collection<ExchangeFactory> factories = exchangeFactoryManager.getExchangeFactories();
            for (ExchangeFactory ef : factories) {
                CompositeType ct = CamelOpenMBeanTypes.listExchangeFactoryCompositeType();
                String routeId = ef.getRouteId();
                String url = ef.getConsumer().getEndpoint().getEndpointUri();
                if (sanitize) {
                    url = URISupport.sanitizeUri(url);
                }

                int capacity = ef.getCapacity();
                int size = ef.getSize();
                long created = 0;
                long acquired = 0;
                long released = 0;
                long discarded = 0;
                if (ef.isStatisticsEnabled()) {
                    created = ef.getStatistics().getCreatedCounter();
                    acquired = ef.getStatistics().getAcquiredCounter();
                    released = ef.getStatistics().getReleasedCounter();
                    discarded = ef.getStatistics().getDiscardedCounter();
                }

                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] { "url", "routeId", "capacity", "pooled", "created", "acquired", "released", "discarded" },
                        new Object[] { url, routeId, capacity, size, created, acquired, released, discarded });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
