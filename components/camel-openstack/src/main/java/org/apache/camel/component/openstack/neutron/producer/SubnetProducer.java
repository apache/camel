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
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.IPVersionType;
import org.openstack4j.model.network.Subnet;
import org.openstack4j.model.network.builder.SubnetBuilder;
import org.openstack4j.openstack.networking.domain.NeutronPool;

public class SubnetProducer extends AbstractOpenstackProducer {

    public SubnetProducer(NeutronEndpoint endpoint, OSClient client) {
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
        final Subnet in = messageToSubnet(exchange.getIn());
        final Subnet out = os.networking().subnet().create(in);
        exchange.getIn().setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.SUBNET_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Subnet ID");
        final Subnet out = os.networking().subnet().get(id);
        exchange.getIn().setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Subnet> out = os.networking().subnet().list();
        exchange.getIn().setBody(out);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.SUBNET_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Subnet ID");
        final ActionResponse response = os.networking().subnet().delete(id);
        checkFailure(response, exchange, "Delete network " + id);
    }


    private Subnet messageToSubnet(Message message) {
        Subnet subnet = message.getBody(Subnet.class);
        if (subnet == null) {
            Map headers = message.getHeaders();
            SubnetBuilder builder = Builders.subnet();

            StringHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            builder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            StringHelper.notEmpty(message.getHeader(NeutronConstants.NETWORK_ID, String.class), "Network ID");
            builder.networkId(message.getHeader(NeutronConstants.NETWORK_ID, String.class));

            ObjectHelper.notNull(message.getHeader(NeutronConstants.IP_VERSION, IPVersionType.class), "IP version");
            builder.ipVersion(message.getHeader(NeutronConstants.IP_VERSION, IPVersionType.class));

            if (headers.containsKey(NeutronConstants.CIDR)) {
                builder.cidr(message.getHeader(NeutronConstants.CIDR, String.class));
            }

            if (headers.containsKey(NeutronConstants.SUBNET_POOL)) {
                final NeutronPool pool = message.getHeader(NeutronConstants.SUBNET_POOL, NeutronPool.class);
                builder.addPool(pool.getStart(), pool.getEnd());
            }

            if (headers.containsKey(NeutronConstants.NETWORK_ID)) {
                builder.networkId(message.getHeader(NeutronConstants.NETWORK_ID, String.class));
            }

            if (headers.containsKey(NeutronConstants.ENABLE_DHCP)) {
                builder.enableDHCP(message.getHeader(NeutronConstants.ENABLE_DHCP, Boolean.class));
            }

            if (headers.containsKey(NeutronConstants.GATEWAY)) {
                builder.gateway(message.getHeader(NeutronConstants.GATEWAY, String.class));
            }

            subnet = builder.build();
        }

        return subnet;
    }
}
