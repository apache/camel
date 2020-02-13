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
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.model.storage.block.VolumeType;
import org.openstack4j.model.storage.block.builder.VolumeBuilder;

public class VolumeProducer extends AbstractOpenstackProducer {

    public VolumeProducer(CinderEndpoint endpoint, OSClient client) {
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
            case CinderConstants.GET_ALL_TYPES:
                doGetAllTypes(exchange);
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
        final Volume in = messageToVolume(msg);
        final Volume out = os.blockStorage().volumes().create(in);
        msg.setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(CinderConstants.VOLUME_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Volume ID");
        final Volume out = os.blockStorage().volumes().get(id);
        msg.setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Volume> out = os.blockStorage().volumes().list();
        exchange.getIn().setBody(out);
    }

    private void doGetAllTypes(Exchange exchange) {
        final List<? extends VolumeType> out = os.blockStorage().volumes().listVolumeTypes();
        exchange.getIn().setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(CinderConstants.VOLUME_ID, String.class), String.class);
        final Volume vol = messageToVolume(msg);
        StringHelper.notEmpty(id, "Cinder Volume ID");
        StringHelper.notEmpty(vol.getDescription(), "Cinder Volume Description");
        StringHelper.notEmpty(vol.getName(), "Cinder Volume Name");
        final ActionResponse out = os.blockStorage().volumes().update(id, vol.getName(), vol.getDescription());
        checkFailure(out, exchange, "Update volume " + id);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(CinderConstants.VOLUME_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Cinder Volume ID");
        final ActionResponse out = os.blockStorage().volumes().delete(id);
        checkFailure(out, exchange, "Delete volume " + id);
    }

    private Volume messageToVolume(Message message) {
        Volume volume = message.getBody(Volume.class);
        if (volume == null) {
            Map headers = message.getHeaders();
            VolumeBuilder builder = Builders.volume();

            final String name = message.getHeader(OpenstackConstants.NAME, String.class);
            StringHelper.notEmpty(name, "Name ");
            builder.name(name);

            if (headers.containsKey(OpenstackConstants.DESCRIPTION)) {
                builder.description(message.getHeader(OpenstackConstants.DESCRIPTION, String.class));
            }

            if (headers.containsKey(CinderConstants.SIZE)) {
                builder.size(message.getHeader(CinderConstants.SIZE, Integer.class));
            }

            if (headers.containsKey(CinderConstants.VOLUME_TYPE)) {
                builder.volumeType(message.getHeader(CinderConstants.VOLUME_TYPE, String.class));
            }

            if (headers.containsKey(CinderConstants.IMAGE_REF)) {
                builder.imageRef(message.getHeader(CinderConstants.IMAGE_REF, String.class));
            }

            if (headers.containsKey(CinderConstants.SNAPSHOT_ID)) {
                builder.snapshot(message.getHeader(CinderConstants.SNAPSHOT_ID, String.class));
            }

            if (headers.containsKey(CinderConstants.IS_BOOTABLE)) {
                builder.bootable(message.getHeader(CinderConstants.IS_BOOTABLE, Boolean.class));
            }

            volume = builder.build();
        }

        return volume;
    }
}
