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
package org.apache.camel;

import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfigurationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Customize Junit5 parallel execution strategy to use more threads than the default 256. The number of threads and the
 * parallelism are set in junit-platform.properties.
 */
public class CamelParallelExecutionStrategy implements ParallelExecutionConfigurationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(CamelParallelExecutionStrategy.class);

    private static final String CONFIG_CUSTOM_PARALLELISM_PROPERTY_NAME = "custom.parallelism";
    private static final String CONFIG_CUSTOM_MAXPOOLSIZE_PROPERTY_NAME = "custom.maxPoolSize";
    private static final int DEFAULT_PARALLELISM = 2;

    int nbParallelExecutions;
    int maxPoolSize;

    private class CamelParallelExecutionConfiguration implements ParallelExecutionConfiguration {

        @Override
        public int getParallelism() {
            return nbParallelExecutions;
        }

        @Override
        public int getMinimumRunnable() {
            return nbParallelExecutions;
        }

        @Override
        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        @Override
        public int getCorePoolSize() {
            return nbParallelExecutions;
        }

        @Override
        public int getKeepAliveSeconds() {
            return 30;
        }

        @Override
        public Predicate<? super ForkJoinPool> getSaturatePredicate() {
            return (ForkJoinPool pool) -> {
                LOG.info("Junit ForkJoinPool saturated: running threads={}, pool size={}, queued tasks={}",
                        pool.getRunningThreadCount(),
                        pool.getPoolSize(),
                        pool.getQueuedTaskCount());
                return true;
            };
        }

    }

    @Override
    public ParallelExecutionConfiguration createConfiguration(ConfigurationParameters configurationParameters) {
        Optional<Integer> parallelism = configurationParameters.get(CONFIG_CUSTOM_PARALLELISM_PROPERTY_NAME,
                Integer::valueOf);
        this.nbParallelExecutions = parallelism.orElse(DEFAULT_PARALLELISM);
        Optional<Integer> poolSize = configurationParameters.get(CONFIG_CUSTOM_MAXPOOLSIZE_PROPERTY_NAME,
                Integer::valueOf);
        this.maxPoolSize = poolSize.orElseGet(() -> nbParallelExecutions * 256);

        LOG.info("Using custom JUnit parallel execution with parallelism={} and maxPoolSize={}",
                nbParallelExecutions, maxPoolSize);

        return new CamelParallelExecutionConfiguration();
    }

}
