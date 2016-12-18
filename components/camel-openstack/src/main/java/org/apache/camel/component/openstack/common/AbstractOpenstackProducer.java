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
package org.apache.camel.component.openstack.common;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.openstack.nova.NovaConstants;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.common.Payloads;

public abstract class AbstractOpenstackProducer extends DefaultProducer {

    protected OSClient os;

    private AbstractOpenstackEndpoint endpoint;

    public AbstractOpenstackProducer(AbstractOpenstackEndpoint endpoint, OSClient client) {
        super(endpoint);
        this.os = client;
        this.endpoint = endpoint;
    }

    protected Payload createPayload(Message msg) {
        //if payload object is send directly
        Payload payload = msg.getBody(Payload.class);
        if (ObjectHelper.isNotEmpty(payload)) {
            return payload;
        }

        Object messageBody = msg.getBody();
        if (messageBody instanceof URL) {
            payload = Payloads.create((URL) messageBody);
        }
        if (messageBody instanceof File) {
            payload = Payloads.create((File) messageBody);
        }
        if (messageBody instanceof InputStream) {
            payload = Payloads.create((InputStream) messageBody);
        }

        if (payload == null) {
            throw new IllegalArgumentException("You have to set payload. It can be InputStream, File or URL class");
        }

        return payload;
    }

    protected String getOperation(Exchange exchange) {
        final String operation = exchange.getIn().getHeader(OpenstackConstants.OPERATION, endpoint.getOperation(), String.class);
        ObjectHelper.notEmpty(operation, "Operation");
        return operation;
    }

    protected void checkFailure(ActionResponse response, Message msg, String operation) {
        msg.setFault(!response.isSuccess());
        if (!response.isSuccess()) {
            msg.setBody(String.format(" %s was not successful: %s", operation, response.getFault()));
        }
    }
}
