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
import org.apache.camel.api.management.mbean.ManagedInflightRepositoryMBean;
import org.apache.camel.spi.InflightRepository;

/**
 *
 */
@ManagedResource(description = "Managed InflightRepository")
public class ManagedInflightRepository extends ManagedService implements ManagedInflightRepositoryMBean {

    private final InflightRepository inflightRepository;

    public ManagedInflightRepository(CamelContext context, InflightRepository inflightRepository) {
        super(context, inflightRepository);
        this.inflightRepository = inflightRepository;
    }

    public InflightRepository getInflightRepository() {
        return inflightRepository;
    }

    @Override
    public int getSize() {
        return inflightRepository.size();
    }

    @Override
    public boolean isInflightBrowseEnabled() {
        return inflightRepository.isInflightBrowseEnabled();
    }

    @Override
    public int size(String routeId) {
        return inflightRepository.size(routeId);
    }

    @Override
    public TabularData browse() {
        return browse(null, -1, false);
    }

    @Override
    public TabularData browse(int limit, boolean sortByLongestDuration) {
        return browse(null, limit, sortByLongestDuration);
    }

    @Override
    public TabularData browse(String routeId, int limit, boolean sortByLongestDuration) {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listInflightExchangesTabularType());
            Collection<InflightRepository.InflightExchange> exchanges
                    = inflightRepository.browse(routeId, limit, sortByLongestDuration);

            for (InflightRepository.InflightExchange entry : exchanges) {
                CompositeType ct = CamelOpenMBeanTypes.listInflightExchangesCompositeType();
                String exchangeId = entry.getExchange().getExchangeId();
                String fromRouteId = entry.getFromRouteId();
                String atRouteId = entry.getAtRouteId();
                String nodeId = entry.getNodeId();
                String elapsed = Long.toString(entry.getElapsed());
                String duration = Long.toString(entry.getDuration());

                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] { "exchangeId", "fromRouteId", "routeId", "nodeId", "elapsed", "duration" },
                        new Object[] { exchangeId, fromRouteId, atRouteId, nodeId, elapsed, duration });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
