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
import org.openstack4j.model.network.AttachInterfaceType;
import org.openstack4j.model.network.Router;
import org.openstack4j.model.network.RouterInterface;
import org.openstack4j.model.network.builder.RouterBuilder;

public class RouterProducer extends AbstractOpenstackProducer {

    public RouterProducer(NeutronEndpoint endpoint, OSClient client) {
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
            case NeutronConstants.ATTACH_INTERFACE:
                doAttach(exchange);
                break;
            case NeutronConstants.DETACH_INTERFACE:
                doDetach(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsuproutered operation " + operation);
        }
    }

    private void doCreate(Exchange exchange) {
        final Router in = messageToRouter(exchange.getIn());
        final Router out = os.networking().router().create(in);
        exchange.getIn().setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.ROUTER_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Router ID");
        final Router result = os.networking().router().get(id);
        msg.setBody(result);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Router> out = os.networking().router().list();
        exchange.getIn().setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Router router = messageToRouter(msg);
        final Router updatedRouter = os.networking().router().update(router);
        msg.setBody(updatedRouter);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NeutronConstants.ROUTER_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Router ID");
        final ActionResponse response = os.networking().router().delete(id);
        checkFailure(response, exchange, "Delete router with ID " + id);
    }

    private void doDetach(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String routerId = msg.getHeader(NeutronConstants.ROUTER_ID, String.class);
        final String subnetId = msg.getHeader(NeutronConstants.SUBNET_ID, String.class);
        final String portId = msg.getHeader(NeutronConstants.PORT_ID, String.class);
        StringHelper.notEmpty(routerId, "Router ID");
        RouterInterface iface = os.networking().router().detachInterface(routerId, subnetId, portId);
        msg.setBody(iface);
    }

    private void doAttach(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String routerId = msg.getHeader(NeutronConstants.ROUTER_ID, String.class);
        final String subnetPortId = msg.getHeader(NeutronConstants.SUBNET_ID, msg.getHeader(NeutronConstants.PORT_ID), String.class);
        final AttachInterfaceType type = msg.getHeader(NeutronConstants.ITERFACE_TYPE, AttachInterfaceType.class);
        StringHelper.notEmpty(routerId, "Router ID");
        StringHelper.notEmpty(subnetPortId, "Subnet/Port ID");
        ObjectHelper.notNull(type, "AttachInterfaceType ");
        RouterInterface routerInterface = os.networking().router().attachInterface(routerId, type, subnetPortId);
        msg.setBody(routerInterface);
    }

    private Router messageToRouter(Message message) {
        Router router = message.getBody(Router.class);

        if (router == null) {
            Map headers = message.getHeaders();
            RouterBuilder builder = Builders.router();

            StringHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            builder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            if (headers.containsKey(NeutronConstants.TENANT_ID)) {
                builder.tenantId(message.getHeader(NeutronConstants.TENANT_ID, String.class));
            }

            router = builder.build();
        }
        return router;
    }
}
