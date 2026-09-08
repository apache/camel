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
package org.apache.camel.component.opa;

import org.apache.camel.Exchange;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class OpaProducer extends DefaultProducer {

    private OpaProducerHealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public OpaProducer(final OpaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpaEndpoint getEndpoint() {
        return (OpaEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        OpaConfiguration configuration = getEndpoint().getConfiguration();
        // an injected client can point anywhere, and the endpoint has no way to ask it where; only probe a
        // server we were told the address of
        if (configuration.getOpaClient() != null || ObjectHelper.isEmpty(configuration.getServerUrl())) {
            return;
        }

        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            producerHealthCheck = new OpaProducerHealthCheck(
                    configuration.getServerUrl(), configuration.getBearerToken(),
                    getEndpoint().getPolicyPath(), getEndpoint().getPolicyPath());
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // the verdict is reported through the decision headers, so that the route can act on it with a
        // filter or a choice; the body is left untouched
        getEndpoint().getEvaluator().evaluate(exchange);
    }
}
