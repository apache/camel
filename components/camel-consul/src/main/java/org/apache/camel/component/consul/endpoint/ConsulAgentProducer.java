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
package org.apache.camel.component.consul.endpoint;

import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.InvokeOnHeader;
import org.kiwiproject.consul.AgentClient;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.model.agent.Registration;

public final class ConsulAgentProducer extends AbstractConsulProducer<AgentClient> {

    public ConsulAgentProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::agentClient);
    }

    @InvokeOnHeader("CHECKS")
    public Object invokeChecks(Message message) throws Exception {
        return getClient().getChecks();
    }

    @InvokeOnHeader("SERVICES")
    public Object invokeServices(Message message) throws Exception {
        return getClient().getServices();
    }

    @InvokeOnHeader("MEMBERS")
    public Object invokeMembers(Message message) throws Exception {
        return getClient().getMembers();
    }

    @InvokeOnHeader("AGENT")
    public Object invokeAgent(Message message) throws Exception {
        return getClient().getAgent();
    }

    @InvokeOnHeader("REGISTER")
    public void invokeRegister(Message message) throws Exception {
        getClient().register(message.getMandatoryBody(Registration.class));
    }

    @InvokeOnHeader("DEREGISTER")
    public void invokeDeregister(Message message) throws Exception {
        getClient().deregister(getMandatoryHeader(message, ConsulConstants.CONSUL_SERVICE_ID, String.class),
                buildQueryOptions(message, getConfiguration()));
    }

}
