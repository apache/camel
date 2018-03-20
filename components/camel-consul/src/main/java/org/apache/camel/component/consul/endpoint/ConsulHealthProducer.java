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
package org.apache.camel.component.consul.endpoint;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.State;
import org.apache.camel.InvokeOnHeader;
import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;

public final class ConsulHealthProducer extends AbstractConsulProducer<HealthClient> {

    public ConsulHealthProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::healthClient);
    }

    @InvokeOnHeader(ConsulHealthActions.NODE_CHECKS)
    protected void nodeChecks(Message message) throws Exception {
        processConsulResponse(
            message,
            getClient().getNodeChecks(
                getMandatoryHeader(message, ConsulConstants.CONSUL_NODE, String.class),
                buildQueryOptions(message, getConfiguration())
            )
        );
    }

    @InvokeOnHeader(ConsulHealthActions.SERVICE_CHECKS)
    protected void serviceChecks(Message message) throws Exception {
        processConsulResponse(
            message,
            getClient().getServiceChecks(
                getMandatoryHeader(message, ConsulConstants.CONSUL_SERVICE, String.class),
                buildQueryOptions(message, getConfiguration())
            )
        );
    }

    @InvokeOnHeader(ConsulHealthActions.SERVICE_INSTANCES)
    protected void serviceInstances(Message message) throws Exception {
        boolean healthyOnly = message.getHeader(ConsulConstants.CONSUL_HEALTHY_ONLY, false, boolean.class);

        if (healthyOnly) {
            processConsulResponse(
                message,
                getClient().getHealthyServiceInstances(
                    getMandatoryHeader(message, ConsulConstants.CONSUL_SERVICE, String.class),
                    buildQueryOptions(message, getConfiguration())
                )
            );
        } else {
            processConsulResponse(
                message,
                getClient().getAllServiceInstances(
                    getMandatoryHeader(message, ConsulConstants.CONSUL_SERVICE, String.class),
                    buildQueryOptions(message, getConfiguration())
                )
            );
        }
    }

    @InvokeOnHeader(ConsulHealthActions.CHECKS)
    protected void checks(Message message) throws Exception {
        processConsulResponse(
            message,
            getClient().getChecksByState(
                getMandatoryHeader(message, ConsulConstants.CONSUL_HEALTHY_STATE, State.class),
                buildQueryOptions(message, getConfiguration())
            )
        );
    }
}
