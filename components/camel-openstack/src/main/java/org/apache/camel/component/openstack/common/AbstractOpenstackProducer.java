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
package org.apache.camel.component.openstack.common;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.OSClient.OSClientV2;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.openstack.OSFactory;

public abstract class AbstractOpenstackProducer extends DefaultProducer {

    protected OSClient os;

    private final AbstractOpenstackEndpoint endpoint;

    // the session with the newest token, as openstack4j re-authenticates an expired token into the session in use
    private volatile OSClient session;

    protected AbstractOpenstackProducer(AbstractOpenstackEndpoint endpoint, OSClient client) {
        super(endpoint);
        this.os = client;
        this.endpoint = endpoint;
        this.session = client;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final OSClient current = useSession();
        try {
            doProcess(exchange);
        } finally {
            session = current;
        }
    }

    /**
     * Processes the exchange with the services of {@link #os}, which use the openstack4j session of the calling thread.
     */
    protected abstract void doProcess(Exchange exchange) throws Exception;

    /**
     * The services of openstack4j use the session of the calling thread (OSClientSession is thread local), and the
     * client was authenticated on the thread that created the producer. Creates the session of the calling thread from
     * the token of the client, as openstack4j documents for using a client from several threads.
     */
    private OSClient useSession() {
        final OSClient client = session;
        if (client instanceof OSClientV3 v3 && v3.getToken() != null) {
            return OSFactory.clientFromToken(v3.getToken(), endpoint.getConfig());
        }
        if (client instanceof OSClientV2 v2 && v2.getAccess() != null) {
            return OSFactory.clientFromAccess(v2.getAccess(), endpoint.getConfig());
        }
        return client;
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
        } else if (messageBody instanceof File) {
            payload = Payloads.create((File) messageBody);
        } else if (messageBody instanceof InputStream) {
            payload = Payloads.create((InputStream) messageBody);
        } else if (messageBody != null) {
            // such as text, bytes or the file of the file consumer
            InputStream is = msg.getBody(InputStream.class);
            if (is != null) {
                payload = Payloads.create(is);
            }
        }

        if (payload == null) {
            throw new IllegalArgumentException(
                    "You have to set payload. It can be InputStream, File or URL class, or a body that converts to InputStream");
        }

        return payload;
    }

    protected String getOperation(Exchange exchange) {
        final String operation
                = exchange.getIn().getHeader(OpenstackConstants.OPERATION, endpoint.getOperation(), String.class);
        StringHelper.notEmpty(operation, "Operation");
        return operation;
    }

    protected void checkFailure(ActionResponse response, Exchange exchange, String operation) {
        if (!response.isSuccess()) {
            exchange.setException(new OpenstackOperationException(operation, response.getFault(), response.getCode()));
        }
    }
}
