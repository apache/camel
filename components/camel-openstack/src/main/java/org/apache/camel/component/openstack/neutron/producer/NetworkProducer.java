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
package org.apache.camel.component.openstack.neutron.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.neutron.NeutronConstants;
import org.apache.camel.component.openstack.neutron.NeutronEndpoint;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.NetworkType;
import org.openstack4j.model.network.builder.NetworkBuilder;

public class NetworkProducer extends AbstractOpenstackProducer {

    public NetworkProducer(NeutronEndpoint endpoint, OSClient client) {
        super(endpoint, client);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final String operation = getOperation(exchange);
        switch (operation) {
            case OpenstackConstants.CREATE:
                doCreate(exchange);
                break;
            case OpenstackConstants.GET:
                doGet(exchange);
                break;
            case OpenstackConstants.GET_ALL:
                doGetAll(exchange);
                break;
            case OpenstackConstants.DELETE:
                doDelete(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    private void doCreate(Exchange exchange) {
        final Network in = messageToNetwork(exchange.getIn());
        final Network out = os.networking().network().create(in);
        exchange.getIn().setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.NETWORK_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Network ID");
        final Network out = os.networking().network().get(id);
        exchange.getIn().setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Network> out = os.networking().network().list();
        exchange.getIn().setBody(out);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.NETWORK_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Network ID");
        final ActionResponse response = os.networking().network().delete(id);
        checkFailure(response, exchange, "Delete network" + id);
    }

    private Network messageToNetwork(Message message) {
        Network network = message.getBody(Network.class);
        if (network == null) {
            Map headers = message.getHeaders();
            NetworkBuilder builder = Builders.network();

            StringHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            builder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            if (headers.containsKey(NeutronConstants.ADMIN_STATE_UP)) {
                builder.adminStateUp(message.getHeader(NeutronConstants.ADMIN_STATE_UP, Boolean.class));
            }

            if (headers.containsKey(NeutronConstants.NETWORK_TYPE)) {
                builder.networkType(message.getHeader(NeutronConstants.NETWORK_TYPE, NetworkType.class));
            }

            if (headers.containsKey(NeutronConstants.IS_SHARED)) {
                builder.isShared(message.getHeader(NeutronConstants.IS_SHARED, Boolean.class));
            }

            if (headers.containsKey(NeutronConstants.IS_ROUTER_EXTERNAL)) {
                builder.isRouterExternal(message.getHeader(NeutronConstants.IS_ROUTER_EXTERNAL, Boolean.class));
            }

            if (headers.containsKey(NeutronConstants.TENANT_ID)) {
                builder.tenantId(message.getHeader(NeutronConstants.TENANT_ID, String.class));
            }

            if (headers.containsKey(NeutronConstants.PHYSICAL_NETWORK)) {
                builder.physicalNetwork(message.getHeader(NeutronConstants.PHYSICAL_NETWORK, String.class));
            }

            if (headers.containsKey(NeutronConstants.SEGMENT_ID)) {
                builder.segmentId(message.getHeader(NeutronConstants.SEGMENT_ID, String.class));
            }

            network = builder.build();
        }

        return network;
    }
}
