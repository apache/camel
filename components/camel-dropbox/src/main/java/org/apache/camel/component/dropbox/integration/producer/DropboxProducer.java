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
package org.apache.camel.component.dropbox.integration.producer;

import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.DropboxEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DropboxProducer extends DefaultProducer {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxProducer.class);

    protected final DropboxEndpoint endpoint;
    protected final DropboxConfiguration configuration;

    public DropboxProducer(DropboxEndpoint endpoint, DropboxConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration.getClient() == null) {
            //create dropbox client
            configuration.createClient();

            LOG.debug("Producer DropBox client created");
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (configuration.getClient() == null) {
            configuration.setClient(null);

            LOG.debug("Producer DropBox client deleted");
        }
        super.doStop();
    }
}
