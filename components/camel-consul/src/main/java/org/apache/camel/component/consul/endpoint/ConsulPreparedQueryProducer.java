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

import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.util.ObjectHelper;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.PreparedQueryClient;
import org.kiwiproject.consul.model.query.PreparedQuery;

public final class ConsulPreparedQueryProducer extends AbstractConsulProducer<PreparedQueryClient> {

    public ConsulPreparedQueryProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::preparedQueryClient);
    }

    @InvokeOnHeader(ConsulPreparedQueryActions.CREATE)
    protected void create(Message message) throws Exception {
        setBodyAndResult(message, getClient().createPreparedQuery(message.getMandatoryBody(PreparedQuery.class)));
    }

    @InvokeOnHeader(ConsulPreparedQueryActions.GET)
    protected void get(Message message) throws Exception {
        String id = message.getHeader(ConsulConstants.CONSUL_PREPARED_QUERY_ID, String.class);

        if (ObjectHelper.isEmpty(id)) {
            setBodyAndResult(message, getClient().getPreparedQuery(message.getMandatoryBody(String.class)));
        } else {
            setBodyAndResult(message, getClient().getPreparedQuery(id));
        }
    }

    @InvokeOnHeader(ConsulPreparedQueryActions.EXECUTE)
    protected void execute(Message message) throws Exception {
        String id = message.getHeader(ConsulConstants.CONSUL_PREPARED_QUERY_ID, String.class);

        if (ObjectHelper.isEmpty(id)) {
            setBodyAndResult(message, getClient().execute(message.getMandatoryBody(String.class)));
        } else {
            setBodyAndResult(message, getClient().execute(id));
        }
    }
}
