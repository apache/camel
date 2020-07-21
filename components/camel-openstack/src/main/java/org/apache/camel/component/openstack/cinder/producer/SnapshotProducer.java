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
package org.apache.camel.component.openstack.cinder.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.cinder.CinderConstants;
import org.apache.camel.component.openstack.cinder.CinderEndpoint;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.storage.block.VolumeSnapshot;
import org.openstack4j.model.storage.block.builder.VolumeSnapshotBuilder;

public class SnapshotProducer extends AbstractOpenstackProducer {

    public SnapshotProducer(CinderEndpoint endpoint, OSClient client) {
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
        final Message msg = exchange.getIn();
        final VolumeSnapshot in = messageToSnapshot(msg);
        final VolumeSnapshot out = os.blockStorage().snapshots().create(in);
        msg.setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(CinderConstants.SNAPSHOT_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Snapshot ID");
        final VolumeSnapshot out = os.blockStorage().snapshots().get(id);
        msg.setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends VolumeSnapshot> out = os.blockStorage().snapshots().list();
        exchange.getIn().setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(CinderConstants.SNAPSHOT_ID, String.class), String.class);
        final VolumeSnapshot vs = messageToSnapshot(msg);
        StringHelper.notEmpty(id, "Cinder Snapshot ID");

        final ActionResponse out = os.blockStorage().snapshots().update(id, vs.getName(), vs.getDescription());
        checkFailure(out, exchange, "Update volume snapshot " + id);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(CinderConstants.SNAPSHOT_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Cinder Snapshot ID");

        final ActionResponse out = os.blockStorage().snapshots().delete(id);
        checkFailure(out, exchange, "Delete snapshot " + id);
    }

    private VolumeSnapshot messageToSnapshot(Message message) {
        VolumeSnapshot volume = message.getBody(VolumeSnapshot.class);
        if (volume == null) {
            Map headers = message.getHeaders();
            VolumeSnapshotBuilder builder = Builders.volumeSnapshot();

            final String name = message.getHeader(OpenstackConstants.NAME, String.class);
            StringHelper.notEmpty(name, "Name");
            builder.name(name);

            if (headers.containsKey(OpenstackConstants.DESCRIPTION)) {
                builder.description(message.getHeader(OpenstackConstants.DESCRIPTION, String.class));
            }

            if (headers.containsKey(CinderConstants.VOLUME_ID)) {
                builder.volume(message.getHeader(CinderConstants.VOLUME_ID, String.class));
            }

            if (headers.containsKey(CinderConstants.FORCE)) {
                builder.force(message.getHeader(CinderConstants.FORCE, Boolean.class));
            }

            volume = builder.build();
        }

        return volume;
    }
}
