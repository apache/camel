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
package org.apache.camel.component.openstack.swift.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.common.OpenstackException;
import org.apache.camel.component.openstack.swift.SwiftConstants;
import org.apache.camel.component.openstack.swift.SwiftEndpoint;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.storage.object.SwiftContainer;
import org.openstack4j.model.storage.object.SwiftHeaders;
import org.openstack4j.model.storage.object.options.ContainerListOptions;
import org.openstack4j.model.storage.object.options.CreateUpdateContainerOptions;

public class ContainerProducer extends AbstractOpenstackProducer {

    public ContainerProducer(SwiftEndpoint endpoint, OSClient client) {
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
            case SwiftConstants.GET_METADATA:
                doGetMetadata(exchange);
                break;
            case SwiftConstants.CREATE_UPDATE_METADATA:
                doUpdateMetadata(exchange);
                break;
            case SwiftConstants.DELETE_METADATA:
                doDeleteMetadata(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    private void doCreate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(OpenstackConstants.NAME, msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class), String.class);
        StringHelper.notEmpty(name, "Container name");

        final CreateUpdateContainerOptions options = messageToCreateUpdateOptions(msg);
        final ActionResponse out = os.objectStorage().containers().create(name, options);
        checkFailure(out, exchange, "Create container " + name);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final ContainerListOptions options = messageToListOptions(msg);
        final List<? extends SwiftContainer> out = os.objectStorage().containers().list(options);
        msg.setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final Message msg = exchange.getIn();
        final List<? extends SwiftContainer> out = os.objectStorage().containers().list();
        msg.setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(OpenstackConstants.NAME, msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class), String.class);
        StringHelper.notEmpty(name, "Container name");
        final CreateUpdateContainerOptions options = messageToCreateUpdateOptions(msg);
        final ActionResponse out = os.objectStorage().containers().update(name, options);
        checkFailure(out, exchange, "Update container " + name);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(OpenstackConstants.NAME, msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class), String.class);
        StringHelper.notEmpty(name, "Container name");
        final ActionResponse out = os.objectStorage().containers().delete(name);
        checkFailure(out, exchange, "Delete container " + name);
    }

    private void doGetMetadata(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(OpenstackConstants.NAME, msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class), String.class);
        StringHelper.notEmpty(name, "Container name");
        msg.setBody(os.objectStorage().containers().getMetadata(name));
    }

    private void doDeleteMetadata(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(OpenstackConstants.NAME, msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class), String.class);
        StringHelper.notEmpty(name, "Container name");
        boolean success = os.objectStorage().containers().deleteMetadata(name, msg.getBody(Map.class));
        if (!success) {
            exchange.setException(new OpenstackException("Removing metadata was not successful"));
        }
    }

    private void doUpdateMetadata(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(OpenstackConstants.NAME, msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class), String.class);
        StringHelper.notEmpty(name, "Container name");
        boolean success = os.objectStorage().containers().updateMetadata(name, msg.getBody(Map.class));
        if (!success) {
            exchange.setException(new OpenstackException("Updating metadata was not successful"));
        }
    }

    private CreateUpdateContainerOptions messageToCreateUpdateOptions(Message message) {
        CreateUpdateContainerOptions options = message.getBody(CreateUpdateContainerOptions.class);
        if (options == null) {
            Map headers = message.getHeaders();
            if (headers.containsKey(SwiftHeaders.CONTAINER_METADATA_PREFIX)) {
                options = getCreateUpdateOptions(options).metadata(message.getHeader(SwiftHeaders.CONTAINER_METADATA_PREFIX, Map.class));
            }

            if (headers.containsKey(SwiftHeaders.VERSIONS_LOCATION)) {
                options = getCreateUpdateOptions(options).versionsLocation(message.getHeader(SwiftHeaders.VERSIONS_LOCATION, String.class));
            }

            if (headers.containsKey(SwiftHeaders.CONTAINER_READ)) {
                options = getCreateUpdateOptions(options).accessRead(message.getHeader(SwiftHeaders.CONTAINER_READ, String.class));
            }

            if (headers.containsKey(SwiftHeaders.CONTAINER_WRITE)) {
                options = getCreateUpdateOptions(options).accessWrite(message.getHeader(SwiftHeaders.CONTAINER_WRITE, String.class));
            }
        }
        return options;
    }

    private CreateUpdateContainerOptions getCreateUpdateOptions(CreateUpdateContainerOptions options) {
        return options == null ? CreateUpdateContainerOptions.create() : options;
    }

    private ContainerListOptions messageToListOptions(Message message) {
        ContainerListOptions options = message.getBody(ContainerListOptions.class);
        if (options == null) {
            Map headers = message.getHeaders();

            if (headers.containsKey(SwiftConstants.LIMIT)) {
                options = getListOptions(options).limit(message.getHeader(SwiftConstants.LIMIT, Integer.class));
            }

            if (headers.containsKey(SwiftConstants.MARKER)) {
                options = getListOptions(options).marker(message.getHeader(SwiftConstants.MARKER, String.class));
            }

            if (headers.containsKey(SwiftConstants.END_MARKER)) {
                options = getListOptions(options).endMarker(message.getHeader(SwiftConstants.END_MARKER, String.class));
            }

            if (headers.containsKey(SwiftConstants.DELIMITER)) {
                options = getListOptions(options).delimiter(message.getHeader(SwiftConstants.DELIMITER, Character.class));
            }

            if (headers.containsKey(SwiftConstants.PATH)) {
                options = getListOptions(options).path(message.getHeader(SwiftConstants.PATH, String.class));
            }
        }
        return options;
    }

    private ContainerListOptions getListOptions(ContainerListOptions options) {
        return options == null ? ContainerListOptions.create() : options;
    }
}
