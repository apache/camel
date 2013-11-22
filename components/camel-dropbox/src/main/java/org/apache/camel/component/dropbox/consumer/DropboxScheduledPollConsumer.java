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
package org.apache.camel.component.dropbox.consumer;

import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.DropboxEndpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class DropboxScheduledPollConsumer extends ScheduledPollConsumer {
    protected static final transient Logger LOG = LoggerFactory.getLogger(DropboxScheduledPollConsumer.class);
    protected DropboxEndpoint endpoint;
    protected DropboxConfiguration configuration;

    public DropboxScheduledPollConsumer(DropboxEndpoint endpoint, Processor processor, DropboxConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    protected abstract int poll() throws Exception;
}
