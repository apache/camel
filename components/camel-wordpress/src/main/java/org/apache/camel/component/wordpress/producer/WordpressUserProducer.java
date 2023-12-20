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
package org.apache.camel.component.wordpress.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.model.User;
import org.apache.camel.component.wordpress.api.service.WordpressServiceUsers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WordpressUserProducer extends AbstractWordpressProducer<User> {
    private static final Logger LOG = LoggerFactory.getLogger(WordpressUserProducer.class);

    private WordpressServiceUsers serviceUsers;

    public WordpressUserProducer(WordpressEndpoint endpoint) {
        super(endpoint);
        this.serviceUsers = WordpressServiceProvider.getInstance().getService(WordpressServiceUsers.class);
    }

    @Override
    protected User processDelete(Exchange exchange) {
        LOG.debug("Trying to delete user {}", getConfiguration().getId());
        return serviceUsers.delete(getConfiguration().getId());
    }

    @Override
    protected User processUpdate(Exchange exchange) {
        LOG.debug("Trying to update the user {} with id {}", exchange.getIn().getBody(), this.getConfiguration().getId());
        return serviceUsers.update(getConfiguration().getId(), exchange.getIn().getBody(User.class));
    }

    @Override
    protected User processInsert(Exchange exchange) {
        LOG.debug("Trying to create a new user{}", exchange.getIn().getBody());
        return serviceUsers.create(exchange.getIn().getBody(User.class));
    }

}
