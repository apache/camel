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
package org.apache.camel.component.openstack.swift.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.AbstractOpenstackProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.swift.SwiftConstants;
import org.apache.camel.component.openstack.swift.SwiftEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.model.storage.object.options.ObjectLocation;

public class ObjectProducer extends AbstractOpenstackProducer {

    public ObjectProducer(SwiftEndpoint endpoint, OSClient client) {
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
        case SwiftConstants.GET_METADATA:
            doGetMetadata(exchange);
            break;
        case SwiftConstants.CREATE_UPDATE_METADATA:
            doUpdateMetadata(exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    private void doCreate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Payload payload = createPayload(msg);
        final String containerName = msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class);
        final String objectName = msg.getHeader(SwiftConstants.OBJECT_NAME, String.class);
        ObjectHelper.notEmpty(containerName, "Container name");
        ObjectHelper.notEmpty(objectName, "Object name");
        final String etag = os.objectStorage().objects().put(containerName, objectName, payload);
        msg.setBody(etag);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String containerName = msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class);
        final String objectName = msg.getHeader(SwiftConstants.OBJECT_NAME, String.class);
        ObjectHelper.notEmpty(containerName, "Container name");
        ObjectHelper.notEmpty(objectName, "Object name");
        final SwiftObject out = os.objectStorage().objects().get(containerName, objectName);
        msg.setBody(out);
    }

    private void doGetAll(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String name = msg.getHeader(SwiftConstants.CONTAINER_NAME, msg.getHeader(OpenstackConstants.NAME, String.class), String.class);
        ObjectHelper.notEmpty(name, "Container name");
        final List<? extends SwiftObject> out = os.objectStorage().objects().list(name);
        exchange.getIn().setBody(out);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String containerName = msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class);
        final String objectName = msg.getHeader(SwiftConstants.OBJECT_NAME, String.class);
        ObjectHelper.notEmpty(containerName, "Container name");
        ObjectHelper.notEmpty(objectName, "Object name");
        final ActionResponse out = os.objectStorage().objects().delete(containerName, objectName);
        msg.setBody(out.getFault());
        msg.setFault(!out.isSuccess());
    }

    private void doGetMetadata(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String containerName = msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class);
        final String objectName = msg.getHeader(SwiftConstants.OBJECT_NAME, String.class);
        ObjectHelper.notEmpty(containerName, "Container name");
        ObjectHelper.notEmpty(objectName, "Object name");

        msg.setBody(os.objectStorage().objects().getMetadata(containerName, objectName));
    }

    private void doUpdateMetadata(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String containerName = msg.getHeader(SwiftConstants.CONTAINER_NAME, String.class);
        final String objectName = msg.getHeader(SwiftConstants.OBJECT_NAME, String.class);
        ObjectHelper.notEmpty(containerName, "Container name");
        ObjectHelper.notEmpty(objectName, "Object name");
        final boolean success = os.objectStorage().objects().updateMetadata(ObjectLocation.create(containerName, objectName), msg.getBody(Map.class));
        msg.setFault(!success);
        if (!success) {
            msg.setBody("Updating metadata was not successful");
        }
    }
}
