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
package org.apache.camel.component.metrics;

import com.codahale.metrics.MetricRegistry;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;


public abstract class AbstractMetricsEndpoint extends DefaultEndpoint {

    protected final MetricRegistry registry;
    protected final String metricsName;

    public AbstractMetricsEndpoint(MetricRegistry registry, String metricsName) {
        this.registry = registry;
        this.metricsName = metricsName;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume from " + getClass().getSimpleName() + ": " + getEndpointUri());
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    public MetricRegistry getRegistry() {
        return registry;
    }

    public String getMetricsName() {
        return metricsName;
    }
}
