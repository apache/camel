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
package org.apache.camel.component.openstack.nova.producer;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.nova.NovaConstants;
import org.apache.camel.component.openstack.nova.NovaEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Keypair;

public class KeypairProducer extends AbstractOpenstackProducer {

    public KeypairProducer(NovaEndpoint endpoint, OSClient client) {
        super(endpoint, client);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = getOperation(exchange);
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
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(OpenstackConstants.NAME, String.class);
        ObjectHelper.notEmpty(name, "Keypair name");

        final String body = msg.getBody(String.class);
        final Keypair kp = os.compute().keypairs().create(name, body);
        msg.setBody(kp);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String keypairName = msg.getHeader(OpenstackConstants.NAME, String.class);
        ObjectHelper.notEmpty(keypairName, "Keypair name");
        final Keypair kp = os.compute().keypairs().get(keypairName);
        msg.setBody(kp);
    }

    private void doGetAll(Exchange exchange) {
        final Message msg = exchange.getIn();
        final List<? extends Keypair> keypairs = os.compute().keypairs().list();
        msg.setBody(keypairs);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String keypairName = msg.getHeader(OpenstackConstants.NAME, String.class);
        ObjectHelper.notEmpty(keypairName, "Keypair name");
        final ActionResponse response = os.compute().keypairs().delete(keypairName);
        checkFailure(response, msg, "Delete keypair " + keypairName);
    }

}
