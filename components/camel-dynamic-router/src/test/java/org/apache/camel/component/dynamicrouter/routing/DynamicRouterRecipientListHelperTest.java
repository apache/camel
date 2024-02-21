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
package org.apache.camel.component.dynamicrouter.routing;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.AggregationStrategyBiFunctionAdapter;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicRouterRecipientListHelperTest {

    @Mock
    CamelContext camelContext;

    @Mock
    Registry mockRegistry;

    @Mock
    BiFunction<Exchange, Exchange, Object> mockBiFunction;

    @Mock
    Object mockBean;

    @Mock
    AggregationStrategy mockStrategy;

    @Mock
    DynamicRouterConfiguration mockConfig;

    @Mock
    RecipientList recipientList;

    @Mock
    ExecutorService newThreadPool;

    @Mock
    ExecutorService existingThreadPool;

    @Mock
    ExecutorServiceManager manager;

    @Mock
    BiFunction<CamelContext, Expression, RecipientList> mockRecipientListSupplier;

    @Mock
    Processor mockProcessor;

    @Mock
    Exchange oldExchange;

    @Mock
    Exchange newExchange;

    @Test
    void testCreateBiFunctionAdapter() {
        when(mockConfig.isAggregationStrategyMethodAllowNull()).thenReturn(true);
        AggregationStrategyBiFunctionAdapter result = DynamicRouterRecipientListHelper.createBiFunctionAdapter.apply(mockBiFunction, mockConfig);
        assertNotNull(result);
        assertTrue(result.isAllowNullNewExchange());
        assertTrue(result.isAllowNullOldExchange());
    }

    @Test
    void testCreateBeanAdapter() {
        when(mockConfig.isAggregationStrategyMethodAllowNull()).thenReturn(true);
        AggregationStrategyBeanAdapter result = DynamicRouterRecipientListHelper.createBeanAdapter.apply(mockBean, mockConfig);
        assertNotNull(result);
        assertTrue(result.isAllowNullNewExchange());
        assertTrue(result.isAllowNullOldExchange());
    }

    @Test
    void testConvertAggregationStrategyWithAggregationStrategyClass() {
        AggregationStrategy result
                = DynamicRouterRecipientListHelper.convertAggregationStrategy.apply(mockStrategy, mockConfig);
        assertSame(mockStrategy, result);
    }

    @Test
    void testConvertAggregationStrategyWithBiFunctionClass() {
        AggregationStrategy result
                = DynamicRouterRecipientListHelper.convertAggregationStrategy.apply(mockBiFunction, mockConfig);
        assertEquals(AggregationStrategyBiFunctionAdapter.class, result.getClass());
    }

    @Test
    void testConvertAggregationStrategyWithBean() {
        AggregationStrategy result = DynamicRouterRecipientListHelper.convertAggregationStrategy.apply(mockBean, mockConfig);
        assertEquals(AggregationStrategyBeanAdapter.class, result.getClass());
    }

    @Test
    void testConvertAggregationStrategyWithNull() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterRecipientListHelper.convertAggregationStrategy.apply(null, mockConfig));
    }

    @Test
    void testCreateProcessor() {
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(mockRecipientListSupplier.apply(eq(camelContext), any(Expression.class))).thenReturn(recipientList);
        Processor processor
                = DynamicRouterRecipientListHelper.createProcessor(camelContext, mockConfig, mockRecipientListSupplier);
        Assertions.assertNotNull(processor);
    }

    @Test
    void testSetPropertiesForRecipientList() {
        // Set up mocking
        when(mockConfig.isParallelProcessing()).thenReturn(true);
        when(mockConfig.isParallelAggregate()).thenReturn(true);
        when(mockConfig.isSynchronous()).thenReturn(true);
        when(mockConfig.isStreaming()).thenReturn(true);
        when(mockConfig.isShareUnitOfWork()).thenReturn(true);
        when(mockConfig.isStopOnException()).thenReturn(true);
        when(mockConfig.isIgnoreInvalidEndpoints()).thenReturn(true);
        when(mockConfig.getCacheSize()).thenReturn(10);
        // Invoke the method under test
        DynamicRouterRecipientListHelper.setPropertiesForRecipientList(recipientList, camelContext, mockConfig);
        // Verify results
        verify(recipientList, times(1)).setParallelProcessing(true);
        verify(recipientList, times(1)).setParallelAggregate(true);
        verify(recipientList, times(1)).setSynchronous(true);
        verify(recipientList, times(1)).setStreaming(true);
        verify(recipientList, times(1)).setShareUnitOfWork(true);
        verify(recipientList, times(1)).setStopOnException(true);
        verify(recipientList, times(1)).setIgnoreInvalidEndpoints(true);
        verify(recipientList, times(1)).setCacheSize(10);
    }

    @Test
    void testSetPropertiesForRecipientListWithGetOnPrepare() {
        // Set up mocking
        when(mockConfig.isParallelProcessing()).thenReturn(true);
        when(mockConfig.getTimeout()).thenReturn(1000L);
        when(mockConfig.getOnPrepare()).thenReturn("onPrepareRef");
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(camelContext.getRegistry().lookupByNameAndType("onPrepareRef", Processor.class))
                .thenReturn(mockProcessor);
        // Invoke the method under test
        DynamicRouterRecipientListHelper.setPropertiesForRecipientList(recipientList, camelContext, mockConfig);
        // Verify results
        verify(recipientList, times(1)).setOnPrepare(mockProcessor);
    }

    @Test
    void testSetPropertiesForRecipientListWithTimeoutAndNotParallelProcessing() {
        // Set up mocking
        when(mockConfig.isParallelProcessing()).thenReturn(false);
        when(mockConfig.getTimeout()).thenReturn(1000L);
        // Invoke the method under test
        Exception ex = assertThrows(IllegalArgumentException.class,
                () ->DynamicRouterRecipientListHelper.setPropertiesForRecipientList(recipientList, camelContext, mockConfig));
        assertEquals("Timeout is used but ParallelProcessing has not been enabled.", ex.getMessage());
    }

    @Test
    void testCreateAggregationStrategyWithInstance() {
        when(mockConfig.getAggregationStrategyBean()).thenReturn(mockStrategy);
        AggregationStrategy strategy = DynamicRouterRecipientListHelper.createAggregationStrategy(camelContext, mockConfig);
        Assertions.assertNotNull(strategy);
    }

    @Test
    void testCreateAggregationStrategyWithRef() {
        when(mockConfig.getAggregationStrategy()).thenReturn("ref");
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(mockRegistry.lookupByNameAndType("ref", Object.class)).thenReturn(mockStrategy);
        AggregationStrategy strategy = DynamicRouterRecipientListHelper.createAggregationStrategy(camelContext, mockConfig);
        Assertions.assertNotNull(strategy);
    }

    @Test
    void testCreateAggregationStrategyWithRefNotFound() {
        when(mockConfig.getAggregationStrategy()).thenReturn("ref");
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(mockRegistry.lookupByNameAndType("ref", Object.class)).thenReturn(null);
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterRecipientListHelper.createAggregationStrategy(camelContext, mockConfig));
        assertEquals("Cannot find AggregationStrategy in Registry with name: ref", ex.getMessage());
    }

    @Test
    void testCreateAggregationStrategyNoOp() {
        when(mockConfig.getAggregationStrategyBean()).thenReturn(null);
        when(mockConfig.getAggregationStrategy()).thenReturn(null);
        AggregationStrategy strategy = DynamicRouterRecipientListHelper.createAggregationStrategy(camelContext, mockConfig);
        Assertions.assertInstanceOf(DynamicRouterRecipientListHelper.NoopAggregationStrategy.class, strategy);
    }

    @Test
    void testCreateAggregationStrategyWithShareUnitOfWorkStrategy() {
        when(mockConfig.isShareUnitOfWork()).thenReturn(true);
        AggregationStrategy strategy = DynamicRouterRecipientListHelper.createAggregationStrategy(camelContext, mockConfig);
        Assertions.assertNotNull(strategy);
    }

    @Test
    void testLookupExecutorServiceRef() {
        String name = "ThreadPool";
        Object source = new Object();
        String executorServiceRef = "ThreadPoolRef";
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(mockRegistry.lookupByNameAndType(executorServiceRef, ExecutorService.class)).thenReturn(existingThreadPool);
        Optional<ExecutorService> executorService
                = DynamicRouterRecipientListHelper.lookupExecutorServiceRef(camelContext, name, source, executorServiceRef);
        Assertions.assertTrue(executorService.isPresent());
    }

    @Test
    void testLookupExecutorServiceRefWithNullManager() {
        String name = "ThreadPool";
        Object source = new Object();
        String executorServiceRef = "ThreadPoolRef";
        when(camelContext.getExecutorServiceManager()).thenReturn(null);
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterRecipientListHelper.lookupExecutorServiceRef(camelContext, name, source,
                        executorServiceRef));
        assertEquals("ExecutorServiceManager must be specified", ex.getMessage());
    }

    @Test
    void testLookupExecutorServiceRefWithNullRef() {
        String name = "ThreadPool";
        Object source = new Object();
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterRecipientListHelper.lookupExecutorServiceRef(camelContext, name, source, null));
        assertEquals("executorServiceRef must be specified", ex.getMessage());
    }

    @Test
    void testLookupExecutorServiceRefWithInvalidRef() {
        String name = "ThreadPool";
        Object source = new Object();
        String executorServiceRef = "InvalidRef";
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        Optional<ExecutorService> executorService
                = DynamicRouterRecipientListHelper.lookupExecutorServiceRef(camelContext, name, source, executorServiceRef);
        Assertions.assertFalse(executorService.isPresent());
    }

    @Test
    void testLookupExecutorServiceRefWithExistingThreadPool() {
        String name = "ThreadPool";
        Object source = new Object();
        String executorServiceRef = "ExistingThreadPool";
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(camelContext.getRegistry().lookupByNameAndType(executorServiceRef, ExecutorService.class))
                .thenReturn(existingThreadPool);
        Optional<ExecutorService> executorService
                = DynamicRouterRecipientListHelper.lookupExecutorServiceRef(camelContext, name, source, executorServiceRef);
        Assertions.assertTrue(executorService.isPresent());
        Assertions.assertEquals(existingThreadPool, executorService.get());
    }

    @Test
    void testLookupExecutorServiceRefWithNewThreadPool() {
        String name = "ThreadPool";
        Object source = new Object();
        String executorServiceRef = "NewThreadPool";
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(manager.newThreadPool(source, name, executorServiceRef)).thenReturn(newThreadPool);
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        Optional<ExecutorService> executorService
                = DynamicRouterRecipientListHelper.lookupExecutorServiceRef(camelContext, name, source, executorServiceRef);
        Assertions.assertTrue(executorService.isPresent());
        Assertions.assertEquals(newThreadPool, executorService.get());
    }

    @Test
    void testLookupByNameAndTypeWithExistingObject() {
        String name = "ExistingObject";
        Object existingObject = new Object();
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(camelContext.getRegistry().lookupByNameAndType(name, Object.class))
                .thenReturn(existingObject);
        Optional<Object> object = DynamicRouterRecipientListHelper.lookupByNameAndType(camelContext, name, Object.class);
        Assertions.assertTrue(object.isPresent());
        Assertions.assertEquals(existingObject, object.get());
    }

    @Test
    void testLookupByNameAndTypeWithNullName() {
        Optional<Object> object = DynamicRouterRecipientListHelper.lookupByNameAndType(camelContext, null, Object.class);
        Assertions.assertFalse(object.isPresent());
    }

    @Test
    void testLookupByNameAndTypeWithReferenceParameter() {
        String name = "#referenceParameter";
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        Optional<Object> object = DynamicRouterRecipientListHelper.lookupByNameAndType(camelContext, name, Object.class);
        Assertions.assertFalse(object.isPresent());
    }

    @Test
    void testLookupByNameAndTypeWithEmptyName() {
        String name = "";
        Optional<Object> object = DynamicRouterRecipientListHelper.lookupByNameAndType(camelContext, name, Object.class);
        Assertions.assertFalse(object.isPresent());
    }

    @Test
    void testWillCreateNewThreadPoolWithExecutorServiceBean() {
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(mockConfig.getExecutorServiceBean()).thenReturn(existingThreadPool);
        assertFalse(DynamicRouterRecipientListHelper.willCreateNewThreadPool(camelContext, mockConfig, true));
    }

    @Test
    void testWillCreateNewThreadPoolWithExecutorServiceRef() {
        String ref = "executorServiceRef";
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(mockConfig.getExecutorService()).thenReturn(ref);
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(camelContext.getRegistry().lookupByNameAndType(ref, ExecutorService.class))
                .thenReturn(existingThreadPool);
        assertFalse(DynamicRouterRecipientListHelper.willCreateNewThreadPool(camelContext, mockConfig, true));
    }

    @Test
    void testWillCreateNewThreadPoolWithDefault() {
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        Assertions.assertTrue(DynamicRouterRecipientListHelper.willCreateNewThreadPool(camelContext, mockConfig, true));
    }

    @Test
    void testGetConfiguredExecutorServiceWithExecutorServiceBean() {
        when(mockConfig.getExecutorServiceBean()).thenReturn(existingThreadPool);
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        ExecutorService result = DynamicRouterRecipientListHelper.getConfiguredExecutorService(camelContext, "someName", mockConfig, true);
        assertEquals(existingThreadPool, result);
    }

    @Test
    void testGetConfiguredExecutorServiceWithExecutorServiceRef() {
        when(mockConfig.getExecutorServiceBean()).thenReturn(null);
        when(mockConfig.getExecutorService()).thenReturn("existingThreadPool");
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(mockRegistry.lookupByNameAndType("existingThreadPool", ExecutorService.class)).thenReturn(existingThreadPool);
        ExecutorService result = DynamicRouterRecipientListHelper.getConfiguredExecutorService(camelContext, "someName", mockConfig, true);
        assertEquals(existingThreadPool, result);
    }

    @Test
    void testGetConfiguredExecutorServiceWithInvalidExecutorServiceRef() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterRecipientListHelper.getConfiguredExecutorService(
                        camelContext, "someName", mockConfig, true));
    }

    @Test
    void testGetConfiguredExecutorServiceWithDefault() {
        when(mockConfig.getExecutorServiceBean()).thenReturn(null);
        when(mockConfig.getExecutorService()).thenReturn(null);
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(manager.newDefaultThreadPool(mockConfig, "someName")).thenReturn(newThreadPool);
        ExecutorService result = DynamicRouterRecipientListHelper.getConfiguredExecutorService(camelContext, "someName", mockConfig, true);
        assertEquals(newThreadPool, result);
    }

    @Test
    void testGetConfiguredExecutorServiceWithoutBeanAndServiceRefAndUseDefaultFalse() {
        when(mockConfig.getExecutorServiceBean()).thenReturn(null);
        when(mockConfig.getExecutorService()).thenReturn(null);
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        ExecutorService result = DynamicRouterRecipientListHelper.getConfiguredExecutorService(camelContext, "someName", mockConfig, false);
        assertNull(result);
    }

    @Test
    void testGetConfiguredExecutorServiceWithReferenceNotFound() {
        String ref = "executorServiceRef";
        when(camelContext.getExecutorServiceManager()).thenReturn(manager);
        when(mockConfig.getExecutorService()).thenReturn(ref);
        when(camelContext.getRegistry()).thenReturn(mockRegistry);
        when(camelContext.getRegistry().lookupByNameAndType(ref, ExecutorService.class))
                .thenReturn(null);
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterRecipientListHelper.getConfiguredExecutorService(camelContext, ref, mockConfig, false));
        assertEquals(
                "ExecutorServiceRef 'executorServiceRef' not found in registry as an ExecutorService instance or as a thread pool profile",
                ex.getMessage());
    }

    @Test
    void testNoOpAggregationStrategy() {
        DynamicRouterRecipientListHelper.NoopAggregationStrategy strategy
                = new DynamicRouterRecipientListHelper.NoopAggregationStrategy();
        Exchange result = strategy.aggregate(oldExchange, newExchange);
        assertEquals(oldExchange, result);
    }

    @Test
    void testNoOpAggregationStrategyWithNullOldExchange() {
        DynamicRouterRecipientListHelper.NoopAggregationStrategy strategy
                = new DynamicRouterRecipientListHelper.NoopAggregationStrategy();
        Exchange result = strategy.aggregate(null, newExchange);
        assertEquals(newExchange, result);
    }

    @Test
    void testNoOpAggregationStrategyWithNullNewExchange() {
        DynamicRouterRecipientListHelper.NoopAggregationStrategy strategy
                = new DynamicRouterRecipientListHelper.NoopAggregationStrategy();
        Exchange result = strategy.aggregate(oldExchange, null);
        assertEquals(oldExchange, result);
    }
}
