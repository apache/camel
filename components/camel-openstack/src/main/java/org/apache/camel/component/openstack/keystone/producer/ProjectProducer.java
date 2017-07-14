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
package org.apache.camel.component.openstack.keystone.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.keystone.KeystoneConstants;
import org.apache.camel.component.openstack.keystone.KeystoneEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.identity.v3.Project;
import org.openstack4j.model.identity.v3.builder.ProjectBuilder;

public class ProjectProducer extends AbstractKeystoneProducer {

    public ProjectProducer(KeystoneEndpoint endpoint, OSClient client) {
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
        default:
            throw new IllegalArgumentException("Unsupported operation " + operation);
        }
    }

    private void doCreate(Exchange exchange) {
        final Project in = messageToProject(exchange.getIn());
        final Project out = osV3Client.identity().projects().create(in);
        exchange.getIn().setBody(out);
    }

    private void doGet(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, String.class);
        ObjectHelper.notEmpty(id, "Project ID");
        final Project result = osV3Client.identity().projects().get(id);
        msg.setBody(result);
    }

    private void doGetAll(Exchange exchange) {
        final List<? extends Project> out = osV3Client.identity().projects().list();
        exchange.getIn().setBody(out);
    }

    private void doUpdate(Exchange exchange) {
        final Message msg = exchange.getIn();
        final Project in = messageToProject(msg);
        final Project out = osV3Client.identity().projects().update(in);
        msg.setBody(out);
    }

    private void doDelete(Exchange exchange) {
        final Message msg = exchange.getIn();
        final String id = msg.getHeader(OpenstackConstants.ID, String.class);
        ObjectHelper.notEmpty(id, "Project ID");
        final ActionResponse response = osV3Client.identity().projects().delete(id);
        checkFailure(response, msg, "Delete project with ID " + id);
    }

    private Project messageToProject(Message message) {
        Project project = message.getBody(Project.class);

        if (project == null) {
            Map headers = message.getHeaders();
            ProjectBuilder builder = Builders.project();

            ObjectHelper.notEmpty(message.getHeader(OpenstackConstants.NAME, String.class), "Name");
            builder.name(message.getHeader(OpenstackConstants.NAME, String.class));

            if (headers.containsKey(KeystoneConstants.DOMAIN_ID)) {
                builder.domainId(message.getHeader(KeystoneConstants.DOMAIN_ID, String.class));
            }

            if (headers.containsKey(KeystoneConstants.DESCRIPTION)) {
                builder.description(message.getHeader(KeystoneConstants.DESCRIPTION, String.class));
            }

            if (headers.containsKey(KeystoneConstants.PARENT_ID)) {
                builder.parentId(message.getHeader(KeystoneConstants.PARENT_ID, String.class));
            }

            project = builder.build();
        }
        return project;
    }
}
