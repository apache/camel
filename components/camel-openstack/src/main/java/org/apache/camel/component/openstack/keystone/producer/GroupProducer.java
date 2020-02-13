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
package org.apache.camel.component.openstack.keystone.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.keystone.KeystoneConstants;
import org.apache.camel.component.openstack.keystone.KeystoneEndpoint;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.identity.v3.Group;
import org.openstack4j.model.identity.v3.builder.GroupBuilder;

public class GroupProducer extends AbstractKeystoneProducer {

    public GroupProducer(KeystoneEndpoint endpoint, OSClient client) {
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
            case KeystoneConstants.ADD_USER_TO_GROUP:
                doAddUser(exchange);
                break;
            case KeystoneConstants.CHECK_GROUP_USER:
                doCheckUserGroup(exchange);
                break;
            case KeystoneConstants.REMOVE_USER_FROM_GROUP:
                doRemoveUserFromGroup(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    private void doCreate(Exchange exchange) {
        final Group in = messageToGroup(exchange.getIn());
        final Group out = osV3Client.identity().groups().create(in);
        exchange.getIn().setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(KeystoneConstants.GROUP_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Group ID");
        final Group result = osV3Client.identity().groups().get(id);
        msg.setBody(result);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Group> out = osV3Client.identity().groups().list();
        exchange.getIn().setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Group group = messageToGroup(msg);
        final Group updatedGroup = osV3Client.identity().groups().update(group);
        msg.setBody(updatedGroup);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, msg.getHeader(KeystoneConstants.GROUP_ID, String.class), String.class);
        StringHelper.notEmpty(id, "Group ID");
        final ActionResponse response = osV3Client.identity().groups().delete(id);
        checkFailure(response, exchange, "Delete group with ID " + id);
    }

    private void doAddUser(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String userId = msg.getHeader(KeystoneConstants.USER_ID, String.class);
        final String groupId = msg.getHeader(KeystoneConstants.GROUP_ID, String.class);
        StringHelper.notEmpty(userId, "User ID");
        StringHelper.notEmpty(groupId, "Group ID");
        final ActionResponse response = osV3Client.identity().groups().addUserToGroup(groupId, userId);
        checkFailure(response, exchange, String.format("Add user %s to group %s", userId, groupId));
    }

    private void doCheckUserGroup(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String userId = msg.getHeader(KeystoneConstants.USER_ID, String.class);
        final String groupId = msg.getHeader(KeystoneConstants.GROUP_ID, String.class);
        StringHelper.notEmpty(userId, "User ID");
        StringHelper.notEmpty(groupId, "Group ID");
        final ActionResponse response = osV3Client.identity().groups().checkGroupUser(groupId, userId);
        msg.setBody(response.isSuccess());
    }

    private void doRemoveUserFromGroup(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String userId = msg.getHeader(KeystoneConstants.USER_ID, String.class);
        final String groupId = msg.getHeader(KeystoneConstants.GROUP_ID, String.class);
        StringHelper.notEmpty(userId, "User ID");
        StringHelper.notEmpty(groupId, "Group ID");
        final ActionResponse response = osV3Client.identity().groups().removeUserFromGroup(groupId, userId);
        checkFailure(response, exchange, String.format("Delete user %s from group %s", userId, groupId));
    }

    private Group messageToGroup(Message message) {
        Group group = message.getBody(Group.class);

        if (group == null) {
            Map headers = message.getHeaders();
            GroupBuilder builder = Builders.group();

            StringHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            builder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            if (headers.containsKey(KeystoneConstants.DOMAIN_ID)) {
                builder.domainId(message.getHeader(KeystoneConstants.DOMAIN_ID, String.class));
            }

            if (headers.containsKey(KeystoneConstants.DESCRIPTION)) {
                builder.description(message.getHeader(KeystoneConstants.DESCRIPTION, String.class));
            }

            group = builder.build();
        }
        return group;
    }
}
