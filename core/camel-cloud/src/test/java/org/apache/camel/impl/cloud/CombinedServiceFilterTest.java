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
package org.apache.camel.impl.cloud;

import java.util.*;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.model.cloud.CombinedServiceCallServiceFilterConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CombinedServiceFilterTest extends ContextTestSupport {
    @Test
    public void testMultiServiceFilterConfiguration() throws Exception {
        CombinedServiceCallServiceFilterConfiguration conf = new CombinedServiceCallServiceFilterConfiguration()
                .healthy()
                .passThrough();

        CombinedServiceFilter filter = (CombinedServiceFilter) conf.newInstance(context);
        assertEquals(2, filter.getDelegates().size());
        assertTrue(filter.getDelegates().get(0) instanceof HealthyServiceFilter);
        assertTrue(filter.getDelegates().get(1) instanceof PassThroughServiceFilter);
    }

    @Test
    public void testMultiServiceFilter() throws Exception {
        CombinedServiceCallServiceFilterConfiguration conf = new CombinedServiceCallServiceFilterConfiguration()
                .healthy()
                .custom((exchange, services) -> services.stream().filter(s -> s.getPort() < 2000).toList());

        Exchange exchange = new DefaultExchange(context);
        List<ServiceDefinition> services = conf.newInstance(context).apply(exchange, Arrays.asList(
                new DefaultServiceDefinition("no-name", "127.0.0.1", 1000),
                new DefaultServiceDefinition("no-name", "127.0.0.1", 1001, new DefaultServiceHealth(false)),
                new DefaultServiceDefinition("no-name", "127.0.0.1", 1002, new DefaultServiceHealth(true)),
                new DefaultServiceDefinition("no-name", "127.0.0.1", 2001, new DefaultServiceHealth(true)),
                new DefaultServiceDefinition("no-name", "127.0.0.1", 2001, new DefaultServiceHealth(false))));

        assertEquals(2, services.size());
        assertFalse(services.stream().anyMatch(s -> !s.getHealth().isHealthy()));
        assertFalse(services.stream().anyMatch(s -> s.getPort() > 2000));
        assertTrue(services.stream().anyMatch(s -> s.getPort() == 1000));
        assertTrue(services.stream().anyMatch(s -> s.getPort() == 1002));
    }

    @Test
    public void testContentBasedServiceFilterCombinedWithServiceFilter() throws Exception {
        CombinedServiceCallServiceFilterConfiguration conf = new CombinedServiceCallServiceFilterConfiguration()
                .healthy()
                .custom((exchange, services) -> services.stream()
                        .filter(serviceDefinition -> ofNullable(serviceDefinition.getMetadata()
                                .get("supports"))
                                .orElse("")
                                .contains(exchange.getProperty("needs", String.class)))
                        .toList());

        Map<String, String> metadata = Collections.singletonMap("supports", "foo,bar");

        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("needs", "foo");

        List<ServiceDefinition> services = conf.newInstance(context).apply(exchange, Arrays.asList(
                new DefaultServiceDefinition("no-name", "127.0.0.1", 2001, metadata, new DefaultServiceHealth(true)),
                new DefaultServiceDefinition("no-name", "127.0.0.1", 2002, metadata, new DefaultServiceHealth(false))));

        assertEquals(1, services.size());
        assertFalse(services.stream().anyMatch(s -> !s.getHealth().isHealthy()));
        assertTrue(services.stream().anyMatch(s -> s.getPort() == 2001));
    }
}
