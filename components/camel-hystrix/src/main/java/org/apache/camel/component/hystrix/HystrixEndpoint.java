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
package org.apache.camel.component.hystrix;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents a Hystrix endpoint.
 */
@UriEndpoint(scheme = "hystrix", title = "Hystrix", syntax = "hystrix:groupKey", producerOnly = true, label = "scheduling,concurrency")
public class HystrixEndpoint extends DefaultEndpoint {

    @UriParam
    private HystrixConfiguration configuration;

    public HystrixEndpoint(String uri, HystrixComponent component, HystrixConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new HystrixProducer(this, configuration);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Hystrix endpoint");
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStop() throws Exception {
        Hystrix.reset();
        HystrixPlugins.getInstance().reset();
        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        HystrixPlugins.getInstance().registerConcurrencyStrategy(new HystrixConcurrencyStrategy() {

            @Override
            public ThreadPoolExecutor getThreadPool(final HystrixThreadPoolKey threadPoolKey,
                                                    HystrixProperty<Integer> corePoolSize, HystrixProperty<Integer> maximumPoolSize,
                                                    HystrixProperty<Integer> keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
                return new ThreadPoolExecutor(corePoolSize.get(), maximumPoolSize.get(), keepAliveTime.get(), unit, workQueue, new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return getCamelContext().getExecutorServiceManager().newThread("camel-hystrix-" + threadPoolKey.name(), r);
                    }
                });
            }
        });

        super.doStart();
    }
}