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
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.AggregationStrategyBiFunctionAdapter;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.RECIPIENT_LIST_EXPRESSION;
import static org.apache.camel.support.CamelContextHelper.mandatoryLookup;

/**
 * Utility class that creates a {@link RecipientList} {@link Processor} based on a {@link DynamicRouterConfiguration}.
 */
public final class DynamicRouterRecipientListHelper {

    private static final String ESM_NAME = "ExecutorServiceManager";

    private DynamicRouterRecipientListHelper() {
    }

    /**
     * Creates an {@link AggregationStrategyBiFunctionAdapter} from a {@link BiFunction} and a
     * {@link DynamicRouterConfiguration}.
     */
    static BiFunction<BiFunction<Exchange, Exchange, Object>, DynamicRouterConfiguration, AggregationStrategyBiFunctionAdapter> createBiFunctionAdapter
            = (bf, cfg) -> {
                AggregationStrategyBiFunctionAdapter adapter = new AggregationStrategyBiFunctionAdapter(bf);
                adapter.setAllowNullNewExchange(cfg.isAggregationStrategyMethodAllowNull());
                adapter.setAllowNullOldExchange(cfg.isAggregationStrategyMethodAllowNull());
                return adapter;
            };

    /**
     * Creates an {@link AggregationStrategyBeanAdapter} from an object and a {@link DynamicRouterConfiguration}.
     */
    static BiFunction<Object, DynamicRouterConfiguration, AggregationStrategyBeanAdapter> createBeanAdapter = (obj, cfg) -> {
        AggregationStrategyBeanAdapter adapter
                = new AggregationStrategyBeanAdapter(obj, cfg.getAggregationStrategyMethodName());
        adapter.setAllowNullNewExchange(cfg.isAggregationStrategyMethodAllowNull());
        adapter.setAllowNullOldExchange(cfg.isAggregationStrategyMethodAllowNull());
        return adapter;
    };

    /**
     * Given an object, convert it to an {@link AggregationStrategy} based on its class.
     */
    @SuppressWarnings({ "unchecked" })
    static BiFunction<Object, DynamicRouterConfiguration, AggregationStrategy> convertAggregationStrategy
            = (aggStr, cfg) -> Optional.ofNullable(aggStr)
                    .filter(AggregationStrategy.class::isInstance)
                    .map(s -> (AggregationStrategy) s)
                    .or(() -> Optional.ofNullable(aggStr)
                            .filter(BiFunction.class::isInstance)
                            .map(s -> createBiFunctionAdapter.apply((BiFunction<Exchange, Exchange, Object>) s, cfg)))
                    .or(() -> Optional.ofNullable(aggStr)
                            .map(s -> createBeanAdapter.apply(s, cfg)))
                    .orElseThrow(() -> new IllegalArgumentException("Cannot convert AggregationStrategy from: " + aggStr));

    /**
     * Create and configure the {@link RecipientList} {@link Processor}. The returned recipient list will be started.
     *
     * @param  camelContext the {@link RecipientList} to configure
     * @param  cfg          the {@link DynamicRouterConfiguration}
     * @return              the configured {@link RecipientList} {@link Processor}
     */
    public static Processor createProcessor(
            CamelContext camelContext, DynamicRouterConfiguration cfg,
            BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier) {
        RecipientList recipientList = recipientListSupplier.apply(camelContext, RECIPIENT_LIST_EXPRESSION);
        setPropertiesForRecipientList(recipientList, camelContext, cfg);
        ExecutorService threadPool
                = getConfiguredExecutorService(camelContext, "RecipientList", cfg, cfg.isParallelProcessing());
        recipientList.setExecutorService(threadPool);
        recipientList.setShutdownExecutorService(
                willCreateNewThreadPool(camelContext, cfg, cfg.isParallelProcessing()));
        recipientList.start();
        return recipientList;
    }

    /**
     * Sets properties on the {@link RecipientList} instance from the {@link DynamicRouterConfiguration}.
     *
     * @param recipientList the {@link RecipientList} to configure
     * @param camelContext  the {@link CamelContext}
     * @param cfg           the {@link DynamicRouterConfiguration}
     */
    static void setPropertiesForRecipientList(
            RecipientList recipientList, CamelContext camelContext, DynamicRouterConfiguration cfg) {
        recipientList.setAggregationStrategy(createAggregationStrategy(camelContext, cfg));
        recipientList.setParallelProcessing(cfg.isParallelProcessing());
        recipientList.setParallelAggregate(cfg.isParallelAggregate());
        recipientList.setSynchronous(cfg.isSynchronous());
        recipientList.setStreaming(cfg.isStreaming());
        recipientList.setShareUnitOfWork(cfg.isShareUnitOfWork());
        recipientList.setStopOnException(cfg.isStopOnException());
        recipientList.setIgnoreInvalidEndpoints(cfg.isIgnoreInvalidEndpoints());
        recipientList.setCacheSize(cfg.getCacheSize());
        if (cfg.getOnPrepare() != null) {
            recipientList.setOnPrepare(mandatoryLookup(camelContext, cfg.getOnPrepare(), Processor.class));
        }
        if (cfg.getTimeout() > 0 && !cfg.isParallelProcessing()) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }
        recipientList.setTimeout(cfg.getTimeout());
    }

    /**
     * Creates the aggregation strategy by preferring a bean set in the configuration, if available. If not, the
     * configuration is checked for an aggregation strategy bean reference, which is looked up. If all else fails, then
     * a {@link UseLatestAggregationStrategy} is used. Then, if "shareUnitOfWork" is set to true in the configuration,
     * the aggregation strategy instance is wrapped in {@link ShareUnitOfWorkAggregationStrategy}.
     *
     * @param  camelContext the camel context
     * @param  cfg          the {@link DynamicRouterConfiguration}
     * @return              the {@link AggregationStrategy}
     */
    static AggregationStrategy createAggregationStrategy(CamelContext camelContext, DynamicRouterConfiguration cfg) {
        AggregationStrategy strategy = Optional.ofNullable(cfg.getAggregationStrategyBean())
                .or(() -> Optional.ofNullable(cfg.getAggregationStrategy())
                        .map(ref -> lookupByNameAndType(camelContext, ref, Object.class)
                                .map(aggStr -> convertAggregationStrategy.apply(aggStr, cfg))
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Cannot find AggregationStrategy in Registry with name: " +
                                                                                cfg.getAggregationStrategy()))))
                .orElse(new NoopAggregationStrategy());
        CamelContextAware.trySetCamelContext(strategy, camelContext);
        return cfg.isShareUnitOfWork() ? new ShareUnitOfWorkAggregationStrategy(strategy) : strategy;
    }

    static <T> Optional<T> lookupByNameAndType(CamelContext camelContext, String name, Class<T> type) {
        return Optional.ofNullable(ObjectHelper.isEmpty(name) ? null : name)
                .map(n -> EndpointHelper.isReferenceParameter(n)
                        ? EndpointHelper.resolveReferenceParameter(camelContext, n, type, false)
                        : camelContext.getRegistry().lookupByNameAndType(n, type));
    }

    /**
     * Determines whether a new thread pool will be created or not.
     * <p/>
     * This is used to know if a new thread pool will be created, and therefore is not shared by others, and therefore
     * exclusive to the cfg.
     *
     * @param  cfg        the node cfg which may leverage executor service.
     * @param  useDefault whether to fall back and use a default thread pool, if no explicit configured
     * @return            <tt>true</tt> if a new thread pool will be created, <tt>false</tt> if not
     * @see               #getConfiguredExecutorService(CamelContext, String, DynamicRouterConfiguration, boolean)
     */
    static boolean willCreateNewThreadPool(CamelContext camelContext, DynamicRouterConfiguration cfg, boolean useDefault) {
        ObjectHelper.notNull(camelContext.getExecutorServiceManager(), ESM_NAME, camelContext);
        return Optional.ofNullable(cfg.getExecutorServiceBean())
                .map(esb -> false)
                .or(() -> Optional.ofNullable(cfg.getExecutorService())
                        .map(es -> lookupByNameAndType(camelContext, es, ExecutorService.class).isEmpty()))
                .orElse(useDefault);
    }

    /**
     * Will look up in {@link org.apache.camel.spi.Registry} for a {@link ExecutorService} registered with the given
     * <tt>executorServiceRef</tt> name.
     * <p/>
     * This method will look up for configured thread pool in the following order
     * <ul>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     *
     * @param  name               name which is appended to the thread name, when the {@link ExecutorService} is created
     *                            based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param  source             the source to use the thread pool
     * @param  executorServiceRef reference name of the thread pool
     * @return                    the executor service, or <tt>null</tt> if none was found.
     */
    static Optional<ExecutorService> lookupExecutorServiceRef(
            CamelContext camelContext, String name, Object source, String executorServiceRef) {
        ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
        ObjectHelper.notNull(manager, ESM_NAME);
        ObjectHelper.notNull(executorServiceRef, "executorServiceRef");
        // lookup in registry first and use existing thread pool if exists,
        // or create a new thread pool, assuming that the executor service ref is a thread pool ID
        return lookupByNameAndType(camelContext, executorServiceRef, ExecutorService.class)
                .or(() -> Optional.ofNullable(manager.newThreadPool(source, name, executorServiceRef)));
    }

    /**
     * Will look up and get the configured {@link ExecutorService} from the given cfg.
     * <p/>
     * This method will look up for configured thread pool in the following order
     * <ul>
     * <li>from the cfg if any explicit configured executor service.</li>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     *
     * @param  name                     name which is appended to the thread name, when the {@link ExecutorService} is
     *                                  created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param  cfg                      the node cfg which may leverage executor service.
     * @param  useDefault               whether to fall back and use a default thread pool, if no explicit configured
     * @return                          the configured executor service, or <tt>null</tt> if none was configured.
     * @throws IllegalArgumentException is thrown if lookup of executor service in {@link org.apache.camel.spi.Registry}
     *                                  was not found
     */
    static ExecutorService getConfiguredExecutorService(
            CamelContext camelContext, String name, DynamicRouterConfiguration cfg, boolean useDefault)
            throws IllegalArgumentException {
        ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
        ObjectHelper.notNull(manager, ESM_NAME, camelContext);
        String exSvcRef = cfg.getExecutorService();
        ExecutorService exSvcBean = cfg.getExecutorServiceBean();
        String errorMessage = "ExecutorServiceRef '" + exSvcRef + "' not found in registry as an ExecutorService " +
                              "instance or as a thread pool profile";
        // The first (preferred) option is to use an explicitly-configured executor if the configuration has it
        return Optional.ofNullable(exSvcBean)
                // The second preference is to check for an executor service reference
                .or(() -> Optional.ofNullable(exSvcRef)
                        // Try to get the referenced executor service
                        .map(r -> lookupExecutorServiceRef(camelContext, name, cfg, r)
                                // But, if the reference is specified in the config,
                                // and could not be obtained, this is an error
                                .orElseThrow(() -> new IllegalArgumentException(errorMessage))))
                // The third and final option is to create a new "default" thread pool if the parameter
                // specifies to that the default thread pool should be used as a fallback
                .or(() -> useDefault ? Optional.of(manager.newDefaultThreadPool(cfg, name)) : Optional.empty())
                // failing the above options, then no executor service is configured
                .orElse(null);
    }

    public static class NoopAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            return oldExchange == null ? newExchange : oldExchange;
        }
    }
}
