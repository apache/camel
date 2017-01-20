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
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.builder.PortBuilder;

public class PortProducer extends AbstractOpenstackProducer {

    public PortProducer(NeutronEndpoint endpoint, OSClient client) {
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
        case OpenstackConstants.UPDATE:
            doUpdate(exchange);
            break;
        case OpenstackConstants.DELETE:
            doDelete(exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    private void doCreate(Exchange exchange) {
        final Port in = messageToPort(exchange.getIn());
        final Port out = os.networking().port().create(in);
        exchange.getIn().setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.PORT_ID, String.class), String.class);
        ObjectHelper.notEmpty(id, "Port ID");
        final Port result = os.networking().port().get(id);
        msg.setBody(result);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Port> out = os.networking().port().list();
        exchange.getIn().setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Port port = messageToPort(msg);
        final Port updatedPort = os.networking().port().update(port);
        msg.setBody(updatedPort);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.PORT_ID, String.class), String.class);
        ObjectHelper.notEmpty(id, "Port ID");
        final ActionResponse response = os.networking().port().delete(id);
        checkFailure(response, msg, "Delete port with ID " + id);
    }

    private Port messageToPort(Message message) {
        Port port = message.getBody(Port.class);

        if (port == null) {
            Map headers = message.getHeaders();
            PortBuilder builder = Builders.port();

            ObjectHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            builder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            if (headers.containsKey(NeutronConstants.TENANT_ID)) {
                builder.tenantId(message.getHeader(NeutronConstants.TENANT_ID, String.class));
            }

            if (headers.containsKey(NeutronConstants.NETWORK_ID)) {
                builder.networkId(message.getHeader(NeutronConstants.NETWORK_ID, String.class));
            }

            if (headers.containsKey(NeutronConstants.DEVICE_ID)) {
                builder.deviceId(message.getHeader(NeutronConstants.DEVICE_ID, String.class));
            }

            if (headers.containsKey(NeutronConstants.MAC_ADDRESS)) {
                builder.macAddress(message.getHeader(NeutronConstants.MAC_ADDRESS, String.class));
            }

            port = builder.build();
        }
        return port;
    }
}
