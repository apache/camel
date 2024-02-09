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
package org.apache.camel.component.knative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.ProducersHealthCheckRepository;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;

public class KnativeProducer extends DefaultAsyncProducer {
    final AsyncProcessor processor;

    private WritableHealthCheckRepository healthCheckRepository;
    private HealthCheck producerHealthCheck;

    public KnativeProducer(Endpoint endpoint, Processor processor, Processor... processors) throws Exception {
        super(endpoint);

        List<Processor> elements = new ArrayList<>(1 + processors.length);
        elements.add(processor);
        Collections.addAll(elements, processors);

        CamelContext camelContext = getEndpoint().getCamelContext();

        Processor pipeline = PluginHelper.getProcessorFactory(camelContext).createProcessor(
                camelContext,
                "Pipeline",
                new Object[] { elements });

        this.processor = AsyncProcessorConverterHelper.convert(pipeline);
    }

    @Override
    public KnativeEndpoint getEndpoint() {
        return (KnativeEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    @Override
    protected void doStart() throws Exception {
        if (getEndpoint().getConfiguration().getSinkBinding() != null) {
            // health-check is optional so discover and resolve
            healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                    getEndpoint().getCamelContext(),
                    ProducersHealthCheckRepository.REPOSITORY_ID,
                    WritableHealthCheckRepository.class);

            if (healthCheckRepository != null) {
                producerHealthCheck = new SinkBindingHealthCheck(getEndpoint());
                producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());

                healthCheckRepository.addHealthCheck(producerHealthCheck);
            }
        }

        ServiceHelper.startService(processor);
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }

        ServiceHelper.stopService(processor);
    }

    @Override
    protected void doSuspend() throws Exception {
        ServiceHelper.suspendService(processor);
    }

    @Override
    protected void doResume() throws Exception {
        ServiceHelper.resumeService(processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(processor);
    }

    public static class SinkBindingHealthCheck extends AbstractHealthCheck {
        private final KnativeEndpoint endpoint;

        public SinkBindingHealthCheck(KnativeEndpoint endpoint) {
            super(endpoint.getId());

            this.endpoint = endpoint;
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            final String kSinkUrl = endpoint.getCamelContext().resolvePropertyPlaceholders("{{k.sink:}}");

            if (ObjectHelper.isNotEmpty(kSinkUrl)) {
                builder.detail("K_SINK", kSinkUrl);
                builder.up();
            } else {
                builder.message("K_SINK not defined");
                builder.down();
            }
        }
    }
}
