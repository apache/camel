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
package org.apache.camel.component.dynamicrouter.integration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Predicate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilterStatistics;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.camel.test.spring.junit5.DisableJmx;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.builder.Builder.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CamelSpringTest
@DisableJmx(false)
@ContextConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DynamicRouterJmxIT {

    static final String SERVICE_NAME = "DynamicRouterControlService";

    static final String CHANNEL_NAME = "test";

    @Autowired
    CamelContext camelContext;

    @EndpointInject("mock:one")
    MockEndpoint mockOne;

    @EndpointInject("mock:two")
    MockEndpoint mockTwo;

    @EndpointInject("mock:three")
    MockEndpoint mockThree;

    @Produce("direct:start")
    ProducerTemplate start;

    @Produce("direct:subscribe")
    ProducerTemplate subscribe;

    MBeanServer getMBeanServer() {
        return camelContext.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    ObjectInstance getServiceMBean() throws Exception {
        MBeanServer mBeanServer = getMBeanServer();
        String serviceName = mBeanServer.queryNames(null, null).stream()
                .map(ObjectName::getCanonicalName)
                .filter(objName -> objName.contains(SERVICE_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Service MBean not found"));
        ObjectName controlServiceObjectName = ObjectName.getInstance(serviceName);
        return mBeanServer.getObjectInstance(controlServiceObjectName);
    }

    void subscribeParticipants() {
        // Create a subscription that accepts an exchange when the message body contains an even number
        // The destination URI is for the endpoint "mockOne"
        Predicate evenPredicate = body().regex("^\\d*[02468]$");
        subscribe.sendBodyAndHeaders("direct:subscribe", evenPredicate,
                Map.of("subscribeChannel", CHANNEL_NAME,
                        "subscriptionId", "evenNumberSubscription",
                        "destinationUri", mockOne.getEndpointUri(),
                        "priority", 2));

        // Create a subscription that accepts an exchange when the message body contains an odd number
        // The destination URI is for the endpoint "mockTwo"
        Predicate oddPredicate = body().regex("^\\d*[13579]$");
        subscribe.sendBodyAndHeaders("direct:subscribe", oddPredicate,
                Map.of("subscribeChannel", CHANNEL_NAME,
                        "subscriptionId", "oddNumberSubscription",
                        "destinationUri", mockTwo.getEndpointUri(),
                        "priority", 2));

        // Create a subscription that accepts an exchange when the message body contains any number
        // The destination URI is for the endpoint "mockThree"
        Predicate allPredicate = body().regex("^\\d+$");
        subscribe.sendBodyAndHeaders("direct:subscribe", allPredicate,
                Map.of("subscribeChannel", CHANNEL_NAME,
                        "subscriptionId", "allNumberSubscription",
                        "destinationUri", mockThree.getEndpointUri(),
                        "priority", 1));
    }

    void sendMessagesAndAssert() throws InterruptedException {
        mockOne.expectedBodiesReceivedInAnyOrder(0, 2, 4, 6, 8, 10);
        mockTwo.expectedBodiesReceivedInAnyOrder(1, 3, 5, 7, 9);
        mockThree.expectedBodiesReceivedInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        IntStream.rangeClosed(0, 10).forEach(n -> start.sendBody(String.valueOf(n)));
        MockEndpoint.assertIsSatisfied(camelContext, 2, TimeUnit.SECONDS);
    }

    @Test
    void testCheckSubscriptions() throws Exception {
        subscribeParticipants();
        MBeanServer mBeanServer = getMBeanServer();
        ObjectInstance serviceMBean = getServiceMBean();
        @SuppressWarnings("unchecked")
        Map<String, ConcurrentSkipListSet<PrioritizedFilter>> subscriptionsMap
                = (Map<String, ConcurrentSkipListSet<PrioritizedFilter>>) mBeanServer.getAttribute(serviceMBean.getObjectName(),
                        "SubscriptionsMap");
        Set<String> actual = subscriptionsMap.get(CHANNEL_NAME).stream()
                .map(PrioritizedFilter::id)
                .collect(Collectors.toSet());
        Set<String> expected = Set.of("evenNumberSubscription", "oddNumberSubscription", "allNumberSubscription");
        assertEquals(expected, actual);
    }

    @Test
    void testCheckStatistics() throws Exception {
        subscribeParticipants();
        sendMessagesAndAssert();
        MBeanServer mBeanServer = getMBeanServer();
        ObjectInstance serviceMBean = getServiceMBean();
        @SuppressWarnings("unchecked")
        Map<String, List<PrioritizedFilterStatistics>> statisticsMap
                = (Map<String, List<PrioritizedFilterStatistics>>) mBeanServer.getAttribute(serviceMBean.getObjectName(),
                        "SubscriptionsStatisticsMap");
        Set<String> actual = statisticsMap.get(CHANNEL_NAME).stream()
                .map(pfs -> String.format("%s: %s", pfs.getFilterId(), pfs.getCount()))
                .collect(Collectors.toSet());
        Set<String> expected = Set.of("evenNumberSubscription: 6", "oddNumberSubscription: 5", "allNumberSubscription: 11");
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRemoveSubscription() throws Exception {
        subscribeParticipants();
        MBeanServer mBeanServer = getMBeanServer();
        ObjectInstance serviceMBean = getServiceMBean();
        Map<String, ConcurrentSkipListSet<PrioritizedFilter>> subscriptionsMap
                = (Map<String, ConcurrentSkipListSet<PrioritizedFilter>>) mBeanServer.getAttribute(serviceMBean.getObjectName(),
                        "SubscriptionsMap");
        Set<String> actualInitial = subscriptionsMap.get(CHANNEL_NAME).stream()
                .map(PrioritizedFilter::id)
                .collect(Collectors.toSet());
        Set<String> expectedInitial = Set.of("evenNumberSubscription", "oddNumberSubscription", "allNumberSubscription");
        assertEquals(expectedInitial, actualInitial);
        boolean result = (boolean) mBeanServer.invoke(serviceMBean.getObjectName(), "removeSubscription",
                new Object[] { CHANNEL_NAME, "evenNumberSubscription" },
                new String[] { String.class.getName(), String.class.getName() });
        assertTrue(result);
        subscriptionsMap = (Map<String, ConcurrentSkipListSet<PrioritizedFilter>>) mBeanServer
                .getAttribute(serviceMBean.getObjectName(), "SubscriptionsMap");
        Set<String> actual = subscriptionsMap.get(CHANNEL_NAME).stream()
                .map(PrioritizedFilter::id)
                .collect(Collectors.toSet());
        Set<String> expected = Set.of("oddNumberSubscription", "allNumberSubscription");
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAddSubscriptionExpression() throws Exception {
        MBeanServer mBeanServer = getMBeanServer();
        ObjectInstance serviceMBean = getServiceMBean();
        String subscriptionName = "trueSubscription";
        String result = (String) mBeanServer.invoke(serviceMBean.getObjectName(), "subscribeWithPredicateExpression",
                new Object[] { CHANNEL_NAME, subscriptionName, mockOne.getEndpointUri(), 2, "true", "simple", false },
                new String[] {
                        String.class.getName(), String.class.getName(), String.class.getName(), int.class.getName(),
                        String.class.getName(), String.class.getName(), boolean.class.getName() });
        assertEquals(subscriptionName, result);
        Map<String, ConcurrentSkipListSet<PrioritizedFilter>> subscriptionsMap
                = (Map<String, ConcurrentSkipListSet<PrioritizedFilter>>) mBeanServer.getAttribute(serviceMBean.getObjectName(),
                        "SubscriptionsMap");
        Set<String> actual = subscriptionsMap.get(CHANNEL_NAME).stream()
                .map(PrioritizedFilter::id)
                .collect(Collectors.toSet());
        Set<String> expected = Set.of("trueSubscription");
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAddSubscriptionBean() throws Exception {
        MBeanServer mBeanServer = getMBeanServer();
        ObjectInstance serviceMBean = getServiceMBean();
        String subscriptionName = "trueSubscription";
        Predicate predicate = PredicateBuilder.constant(true);
        camelContext.getRegistry().bind("truePredicate", predicate);
        String result = (String) mBeanServer.invoke(serviceMBean.getObjectName(), "subscribeWithPredicateBean",
                new Object[] { CHANNEL_NAME, subscriptionName, mockOne.getEndpointUri(), 2, "truePredicate", false },
                new String[] {
                        String.class.getName(), String.class.getName(), String.class.getName(), int.class.getName(),
                        String.class.getName(), boolean.class.getName() });
        assertEquals(subscriptionName, result);
        Map<String, ConcurrentSkipListSet<PrioritizedFilter>> subscriptionsMap
                = (Map<String, ConcurrentSkipListSet<PrioritizedFilter>>) mBeanServer.getAttribute(serviceMBean.getObjectName(),
                        "SubscriptionsMap");
        Set<String> actual = subscriptionsMap.get(CHANNEL_NAME).stream()
                .map(PrioritizedFilter::id)
                .collect(Collectors.toSet());
        Set<String> expected = Set.of("trueSubscription");
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAddSubscriptionInstance() throws Exception {
        MBeanServer mBeanServer = getMBeanServer();
        ObjectInstance serviceMBean = getServiceMBean();
        String subscriptionName = "trueSubscription";
        Predicate predicate = PredicateBuilder.constant(true);
        String result = (String) mBeanServer.invoke(serviceMBean.getObjectName(), "subscribeWithPredicateInstance",
                new Object[] { CHANNEL_NAME, subscriptionName, mockOne.getEndpointUri(), 2, predicate, false },
                new String[] {
                        String.class.getName(), String.class.getName(), String.class.getName(), int.class.getName(),
                        Object.class.getName(), boolean.class.getName() });
        assertEquals(subscriptionName, result);
        Map<String, ConcurrentSkipListSet<PrioritizedFilter>> subscriptionsMap
                = (Map<String, ConcurrentSkipListSet<PrioritizedFilter>>) mBeanServer.getAttribute(serviceMBean.getObjectName(),
                        "SubscriptionsMap");
        Set<String> actual = subscriptionsMap.get(CHANNEL_NAME).stream()
                .map(PrioritizedFilter::id)
                .collect(Collectors.toSet());
        Set<String> expected = Set.of("trueSubscription");
        assertEquals(expected, actual);
    }
}
