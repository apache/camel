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
package org.apache.camel.component.wordpress.consumer;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Processor;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.model.User;
import org.apache.camel.component.wordpress.api.model.UserSearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressServiceUsers;

public class WordpressUserConsumer extends AbstractWordpressConsumer {

    private WordpressServiceUsers serviceUsers;

    public WordpressUserConsumer(WordpressEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        serviceUsers = WordpressServiceProvider.getInstance().getService(WordpressServiceUsers.class);
    }

    public WordpressUserConsumer(WordpressEndpoint endpoint, Processor processor, ScheduledExecutorService scheduledExecutorService) {
        super(endpoint, processor, scheduledExecutorService);
        serviceUsers = WordpressServiceProvider.getInstance().getService(WordpressServiceUsers.class);
    }

    @Override
    protected int poll() throws Exception {
        if (getConfiguration().getId() == null) {
            return this.pollForList();
        } else {
            return this.pollForSingle();
        }
    }

    private int pollForSingle() {
        final User user = this.serviceUsers.retrieve(getConfiguration().getId());
        if (user == null) {
            return 0;
        }
        this.process(user);
        return 1;
    }

    private int pollForList() {
        final List<User> users = this.serviceUsers.list((UserSearchCriteria)getConfiguration().getSearchCriteria());
        users.stream().forEach(p -> this.process(p));
        LOG.trace("returned users is {}", users);
        return users.size();
    }

}
