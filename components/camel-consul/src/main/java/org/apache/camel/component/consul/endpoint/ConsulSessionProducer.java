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
package org.apache.camel.component.consul.endpoint;

import com.orbitz.consul.Consul;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.model.session.Session;
import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.util.ObjectHelper;

public final class ConsulSessionProducer extends AbstractConsulProducer<SessionClient> {

    public ConsulSessionProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::sessionClient);
    }

    @InvokeOnHeader(ConsulSessionActions.CREATE)
    protected void create(Message message) throws Exception {
        setBodyAndResult(message, getClient().createSession(message.getMandatoryBody(Session.class), message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class)));
    }

    @InvokeOnHeader(ConsulSessionActions.DESTROY)
    protected void destroy(Message message) throws Exception {
        String sessionId = message.getHeader(ConsulConstants.CONSUL_SESSION, String.class);

        if (ObjectHelper.isEmpty(sessionId)) {
            getClient().destroySession(message.getMandatoryBody(String.class), message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class));
        } else {
            getClient().destroySession(sessionId, message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class));
        }

        setBodyAndResult(message, null, true);
    }

    @InvokeOnHeader(ConsulSessionActions.INFO)
    protected void info(Message message) throws Exception {
        String sessionId = message.getHeader(ConsulConstants.CONSUL_SESSION, String.class);

        if (ObjectHelper.isEmpty(sessionId)) {
            setBodyAndResult(message,
                             getClient().getSessionInfo(message.getMandatoryBody(String.class), message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class)).orElse(null));
        } else {
            setBodyAndResult(message, getClient().getSessionInfo(sessionId, message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class)).orElse(null));
        }
    }

    @InvokeOnHeader(ConsulSessionActions.LIST)
    protected void list(Message message) throws Exception {
        setBodyAndResult(message, getClient().listSessions(message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class)));
    }

    @InvokeOnHeader(ConsulSessionActions.RENEW)
    protected void renew(Message message) throws Exception {
        String sessionId = message.getHeader(ConsulConstants.CONSUL_SESSION, String.class);

        if (ObjectHelper.isEmpty(sessionId)) {
            setBodyAndResult(message, getClient().renewSession(message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class), message.getMandatoryBody(String.class)));
        } else {
            setBodyAndResult(message, getClient().renewSession(message.getHeader(ConsulConstants.CONSUL_DATACENTER, String.class), sessionId));
        }
    }
}
