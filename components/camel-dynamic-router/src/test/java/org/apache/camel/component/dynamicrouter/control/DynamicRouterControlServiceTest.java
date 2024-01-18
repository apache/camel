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
package org.apache.camel.component.dynamicrouter.control;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.spi.Language;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.builder.PredicateBuilder.constant;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.ERROR_PREDICATE_CLASS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class DynamicRouterControlServiceTest {

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    DynamicRouterFilterService filterService;

    CamelContext context;

    DynamicRouterControlService service;

    final String subscribeChannel = "test";

    final String subscriptionId = "testId";

    final String destinationUri = "testUri";

    final int priority = 10;

    final String predicateExpression = "true";

    final String expressionLanguage = "simple";

    final Predicate predicateInstance = constant(true);

    final String predicateBeanName = "testPredicate";

    @BeforeEach
    void setup() {
        context = contextExtension.getContext();
        service = new DynamicRouterControlService(context, filterService);
    }

    @Test
    void obtainPredicateFromBeanName() {
        context.getRegistry().bind(predicateBeanName, Predicate.class, predicateInstance);
        Predicate actualPredicate = DynamicRouterControlService.obtainPredicateFromBeanName(predicateBeanName, context);
        assertEquals(predicateInstance, actualPredicate);
    }

    @Test
    void obtainPredicateFromExpression() {
        String trueExpression = "true";
        Language language = context.resolveLanguage(expressionLanguage);
        Predicate expectedPredicate = language.createPredicate(trueExpression);
        Predicate actualPredicate = DynamicRouterControlService
                .obtainPredicateFromExpression(context, trueExpression, expressionLanguage);
        assertEquals(expectedPredicate, actualPredicate);
    }

    @Test
    void obtainPredicateFromBody() {
        Predicate expectedPredicate = PredicateBuilder.constant(true);
        Predicate actualPredicate = DynamicRouterControlService.obtainPredicateFromInstance(expectedPredicate);
        assertEquals(expectedPredicate, actualPredicate);
    }

    @Test
    void obtainPredicateFromInstanceWhenNotPredicateInstance() {
        String expectedPredicate = "thisMightHurt";
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterControlService.obtainPredicateFromInstance(expectedPredicate));
        assertEquals(ERROR_PREDICATE_CLASS, ex.getMessage());
    }

    @Test
    void obtainPredicateFromExpressionWithError() {
        String expression = "not a valid expression";
        assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterControlService.obtainPredicateFromExpression(context, expression, expressionLanguage));
    }

    @Test
    void subscribeWithPredicateExpression() {
        service.subscribeWithPredicateExpression(subscribeChannel, subscriptionId, destinationUri, priority,
                predicateExpression, expressionLanguage, false);
        Mockito.verify(filterService, Mockito.times(1))
                .addFilterForChannel(
                        eq(subscriptionId), eq(priority), any(Predicate.class),
                        eq(destinationUri), eq(subscribeChannel), eq(false));
    }

    @Test
    void subscribeWithPredicateBean() {
        context.getRegistry().bind(predicateBeanName, Predicate.class, predicateInstance);
        service.subscribeWithPredicateBean(subscribeChannel, subscriptionId, destinationUri, priority,
                predicateBeanName, false);
        Mockito.verify(filterService, Mockito.times(1))
                .addFilterForChannel(
                        eq(subscriptionId), eq(priority), any(Predicate.class),
                        eq(destinationUri), eq(subscribeChannel), eq(false));
    }

    @Test
    void subscribeWithPredicateInstance() {
        service.subscribeWithPredicateInstance(subscribeChannel, subscriptionId, destinationUri, priority,
                predicateInstance, false);
        Mockito.verify(filterService, Mockito.times(1))
                .addFilterForChannel(
                        eq(subscriptionId), eq(priority), any(Predicate.class),
                        eq(destinationUri), eq(subscribeChannel), eq(false));
    }

    @Test
    void removeSubscription() {
        service.removeSubscription(subscribeChannel, subscriptionId);
        Mockito.verify(filterService, Mockito.times(1))
                .removeFilterById(subscriptionId, subscribeChannel);
    }

    @Test
    void testGetSubscriptionsForChannel() {
        String channel = "test";
        service.getSubscriptionsForChannel(channel);
        Mockito.verify(filterService, Mockito.times(1)).getFiltersForChannel(channel);
    }

    @Test
    void testGetStatisticsForChannel() {
        String channel = "test";
        service.getStatisticsForChannel(channel);
        Mockito.verify(filterService, Mockito.times(1)).getFiltersForChannel(channel);
    }
}
