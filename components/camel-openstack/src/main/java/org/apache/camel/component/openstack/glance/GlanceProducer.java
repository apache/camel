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
package org.apache.camel.component.openstack.glance;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.image.ContainerFormat;
import org.openstack4j.model.image.DiskFormat;
import org.openstack4j.model.image.Image;
import org.openstack4j.model.image.builder.ImageBuilder;

public class GlanceProducer extends AbstractOpenstackProducer {

    public GlanceProducer(GlanceEndpoint endpoint, OSClient client) {
        super(endpoint, client);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = getOperation(exchange);

        switch (operation) {
            case GlanceConstants.RESERVE:
                doReserve(exchange);
                break;
            case OpenstackConstants.CREATE:
                doCreate(exchange);
                break;
            case OpenstackConstants.UPDATE:
                doUpdate(exchange);
                break;
            case GlanceConstants.UPLOAD:
                doUpload(exchange);
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

    private void doReserve(Exchange exchange) {
        final Image in = messageToImage(exchange.getIn());
        final Image out = os.images().reserve(in);
        exchange.getIn().setBody(out);
    }

    private void doCreate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Image in = messageHeadersToImage(msg, true);
        final Payload payload = createPayload(msg);
        final Image out = os.images().create(in, payload);
        msg.setBody(out);
    }

    private void doUpload(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String imageId = msg.getHeader(OpenstackConstants.ID, String.class);
        StringHelper.notEmpty(imageId, "Image ID");
        final Image in = messageHeadersToImage(msg, false);
        final Payload payload = createPayload(msg);
        final Image out = os.images().upload(imageId, payload, in);
        msg.setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Image in = messageToImage(msg);
        final Image out = os.images().update(in);
        msg.setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String imageId = msg.getHeader(OpenstackConstants.ID, String.class);
        StringHelper.notEmpty(imageId, "ImageID");
        final Image out = os.images().get(imageId);
        msg.setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Image> out = os.images().list();
        exchange.getIn().setBody(out);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String imageId = msg.getHeader(OpenstackConstants.ID, String.class);
        StringHelper.notEmpty(imageId, "ImageID");
        final ActionResponse response = os.compute().images().delete(imageId);
        checkFailure(response, exchange, "Delete image " + imageId);
    }

    private Image messageToImage(Message message) {
        Image image = message.getBody(Image.class);
        if (image == null) {
            image = messageHeadersToImage(message, true);
        }

        return image;
    }

    private Image messageHeadersToImage(Message message, boolean required) {
        ImageBuilder imageBuilder = null;

        if (required && ObjectHelper.isEmpty(message.getHeader(OpenstackConstants.NAME, String.class))) {
            throw new IllegalArgumentException("Image Name must be specified and not empty");
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(OpenstackConstants.NAME, String.class))) {
            imageBuilder = getImageBuilder(imageBuilder).name(message.getHeader(OpenstackConstants.NAME, String.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.DISK_FORMAT, DiskFormat.class))) {
            imageBuilder = getImageBuilder(imageBuilder).diskFormat(message.getHeader(GlanceConstants.DISK_FORMAT, DiskFormat.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.CONTAINER_FORMAT, ContainerFormat.class))) {
            imageBuilder = getImageBuilder(imageBuilder).containerFormat(message.getHeader(GlanceConstants.CONTAINER_FORMAT, ContainerFormat.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.SIZE, Long.class))) {
            imageBuilder = getImageBuilder(imageBuilder).size(message.getHeader(GlanceConstants.SIZE, Long.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.CHECKSUM))) {
            imageBuilder = getImageBuilder(imageBuilder).checksum(message.getHeader(GlanceConstants.CHECKSUM, String.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.MIN_DISK))) {
            imageBuilder = getImageBuilder(imageBuilder).minDisk(message.getHeader(GlanceConstants.MIN_DISK, Long.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.MIN_RAM))) {
            imageBuilder = getImageBuilder(imageBuilder).minRam(message.getHeader(GlanceConstants.MIN_RAM, Long.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.OWNER))) {
            imageBuilder = getImageBuilder(imageBuilder).owner(message.getHeader(GlanceConstants.OWNER, String.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(GlanceConstants.IS_PUBLIC))) {
            imageBuilder = getImageBuilder(imageBuilder).isPublic(message.getHeader(GlanceConstants.IS_PUBLIC, Boolean.class));
        }

        if (ObjectHelper.isNotEmpty(message.getHeader(OpenstackConstants.PROPERTIES))) {
            imageBuilder = getImageBuilder(imageBuilder).properties(message.getHeader(OpenstackConstants.PROPERTIES, Map.class));
        }

        if (!required && imageBuilder == null) {
            return null;
        }
        ObjectHelper.notNull(imageBuilder, "Image");
        return imageBuilder.build();
    }

    private ImageBuilder getImageBuilder(ImageBuilder builder) {
        return builder == null ? Builders.image() : builder;
    }
}
