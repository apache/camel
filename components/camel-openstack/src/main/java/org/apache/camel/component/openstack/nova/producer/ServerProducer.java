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
package org.apache.camel.component.openstack.nova.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.nova.NovaConstants;
import org.apache.camel.component.openstack.nova.NovaEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Action;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;

public class ServerProducer extends AbstractOpenstackProducer {

    public ServerProducer(NovaEndpoint endpoint, OSClient client) {
        super(endpoint, client);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final String operation = getOperation(exchange);
        switch (operation) {
            case OpenstackConstants.CREATE:
                doCreate(exchange);
                break;
            case NovaConstants.CREATE_SNAPSHOT:
                doCreateSnapshot(exchange);
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
            case NovaConstants.ACTION:
                doAction(exchange);
                break;
            default:
                //execute action when Operation:Action header is not set but
                // Action is properly specified
                if (exchange.getIn().getHeaders().containsKey(NovaConstants.ACTION)) {
                    doAction(exchange);
                } else {
                    throw new IllegalArgumentException("Unsupported operation " + operation);
                }
        }
    }

    private void doCreate(Exchange exchange) {
        final ServerCreate in = messageToServer(exchange.getIn());
        final Server out = os.compute().servers().boot(in);
        exchange.getIn().setBody(out);
    }

    private void doCreateSnapshot(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String serverId = msg.getHeader(OpenstackConstants.ID, String.class);
        final String name = msg.getHeader(OpenstackConstants.NAME, String.class);
        StringHelper.notEmpty(serverId, "Server ID");
        StringHelper.notEmpty(name, "VolumeSnapshot name");

        final String snapshotId = os.compute().servers().createSnapshot(serverId, name);
        msg.setBody(snapshotId);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String serverId = msg.getHeader(OpenstackConstants.ID, String.class);
        StringHelper.notEmpty(serverId, "Server ID");
        final Server result = os.compute().servers().get(serverId);
        msg.setBody(result);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Server> out = os.compute().servers().list();
        exchange.getIn().setBody(out);
    }

    private void doAction(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Action action = msg.getHeader(NovaConstants.ACTION, Action.class);
        final String serverId = msg.getHeader(OpenstackConstants.ID, String.class);
        ObjectHelper.notNull(action, "Server action");
        StringHelper.notEmpty(serverId, "Server ID");
        final ActionResponse response = os.compute().servers().action(serverId, action);
        checkFailure(response, exchange, "Performing action " + action.name());
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String serverId = msg.getHeader(OpenstackConstants.ID, String.class);
        StringHelper.notEmpty(serverId, "Server ID");
        final ActionResponse response = os.compute().servers().delete(serverId);
        checkFailure(response, exchange, "Delete server with ID " + serverId);
    }

    private ServerCreate messageToServer(Message message) {
        ServerCreate serverCreate = message.getBody(ServerCreate.class);

        if (serverCreate == null) {
            Map headers = message.getHeaders();
            ServerCreateBuilder builder = Builders.server();

            StringHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            builder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            if (headers.containsKey(NovaConstants.IMAGE_ID)) {
                builder.image(message.getHeader(NovaConstants.IMAGE_ID, String.class));
            }

            if (headers.containsKey(NovaConstants.NETWORK)) {
                builder.networks(message.getHeader(NovaConstants.NETWORK, List.class));
            }

            if (headers.containsKey(NovaConstants.FLAVOR_ID)) {
                builder.flavor(message.getHeader(NovaConstants.FLAVOR_ID, String.class));
            }

            if (headers.containsKey(NovaConstants.KEYPAIR_NAME)) {
                builder.keypairName(message.getHeader(NovaConstants.KEYPAIR_NAME, String.class));
            }

            if (headers.containsKey(NovaConstants.ADMIN_PASSWORD)) {
                builder.addAdminPass(message.getHeader(NovaConstants.ADMIN_PASSWORD, String.class));
            }

            serverCreate = builder.build();
        }
        return serverCreate;
    }
}
