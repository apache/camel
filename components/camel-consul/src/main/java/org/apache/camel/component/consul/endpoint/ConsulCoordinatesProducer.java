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

import com.orbitz.consul.Consul;
import com.orbitz.consul.CoordinateClient;
import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.InvokeOnHeader;

public final class ConsulCoordinatesProducer extends AbstractConsulProducer<CoordinateClient> {

    public ConsulCoordinatesProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::coordinateClient);
    }

    @InvokeOnHeader(ConsulCoordinatesActions.DATACENTERS)
    protected void datacenters(Message message) throws Exception {
        setBodyAndResult(message, getClient().getDatacenters());
    }

    @InvokeOnHeader(ConsulCoordinatesActions.NODES)
    protected void nodes(Message message) throws Exception {
        setBodyAndResult(message, getClient().getNodes(message.getHeader(ConsulConstants.CONSUL_DATACENTER, getConfiguration().getDatacenter(), String.class)));
    }
}
