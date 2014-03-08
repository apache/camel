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
package org.apache.camel.web.util;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.web.resources.CamelContextResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class JMXRouteStatisticsTest extends Assert {
    private AbstractXmlApplicationContext applicationContext;
    private CamelContext camelContext;
    private RouteStatistics statistics = new JMXRouteStatistics();
 
    @Test
    public void testRouteStats() throws Exception {
        CamelContextResource resource = new CamelContextResource(camelContext);
        RoutesDefinition routes = resource.getRoutesResource().getRouteDefinitions();
        List<RouteDefinition> list = routes.getRoutes();
        Object exchangesCompleted = statistics.getRouteStatistic(camelContext, list.get(0).getId(), "ExchangesCompleted");
        assertEquals("JMX value incorrect, should be 0", new Long(0), exchangesCompleted);

        NotifyBuilder notify = new NotifyBuilder(camelContext).whenDone(1).create();

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody("seda:foo", "test");

        notify.matchesMockWaitTime();

        exchangesCompleted = statistics.getRouteStatistic(camelContext, list.get(0).getId(), "ExchangesCompleted");
        assertEquals("JMX value incorrect, should be 1", new Long(1), exchangesCompleted);
    }

    @Before
    public void setUp() throws Exception {
        applicationContext = new FileSystemXmlApplicationContext("src/main/webapp/WEB-INF/applicationContext.xml");
        applicationContext.start();
        Map<String, CamelContext> beansOfType = applicationContext.getBeansOfType(CamelContext.class);
        camelContext = beansOfType.isEmpty() ? null : beansOfType.values().iterator().next();
        assertNotNull("camelContext", camelContext);
    }

    @After
    public void tearDown() throws Exception {
        if (applicationContext != null) {
            applicationContext.stop();
        }
    }
}
