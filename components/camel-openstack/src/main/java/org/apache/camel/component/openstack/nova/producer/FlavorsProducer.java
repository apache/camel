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
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.builder.FlavorBuilder;

public class FlavorsProducer extends AbstractOpenstackProducer {

    public FlavorsProducer(NovaEndpoint endpoint, OSClient client) {
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
        final Flavor in = messageToFlavor(exchange.getIn());
        final Flavor out = os.compute().flavors().create(in);
        exchange.getIn().setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String flavorId = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NovaConstants.FLAVOR_ID, String.class), String.class);
        StringHelper.notEmpty(flavorId, "FlavorID");
        final Flavor out = os.compute().flavors().get(flavorId);
        exchange.getIn().setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Flavor> out = os.compute().flavors().list();
        exchange.getIn().setBody(out);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String flavorId = msg.getHeader(OpenstackConstants.ID, msg.getHeader(NovaConstants.FLAVOR_ID, String.class), String.class);
        StringHelper.notEmpty(flavorId, "FlavorID");
        final ActionResponse response = os.compute().flavors().delete(flavorId);
        checkFailure(response, exchange, "Delete flavor");
    }

    private org.openstack4j.model.compute.Flavor messageToFlavor(Message message) {
        Flavor flavor = message.getBody(Flavor.class);
        if (flavor == null) {
            Map headers = message.getHeaders();
            FlavorBuilder flavorBuilder = Builders.flavor();

            StringHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            flavorBuilder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            if (headers.containsKey(NovaConstants.VCPU)) {
                flavorBuilder.vcpus(message.getHeader(NovaConstants.VCPU, Integer.class));
            }

            if (headers.containsKey(NovaConstants.RAM)) {
                flavorBuilder.ram(message.getHeader(NovaConstants.RAM, Integer.class));
            }

            if (headers.containsKey(NovaConstants.DISK)) {
                flavorBuilder.disk(message.getHeader(NovaConstants.DISK, Integer.class));
            }

            if (headers.containsKey(NovaConstants.SWAP)) {
                flavorBuilder.swap(message.getHeader(NovaConstants.SWAP, Integer.class));
            }

            if (headers.containsKey(NovaConstants.RXTXFACTOR)) {
                flavorBuilder.rxtxFactor(message.getHeader(NovaConstants.RXTXFACTOR, Integer.class));
            }

            flavor = flavorBuilder.build();
        }

        return flavor;
    }
}
