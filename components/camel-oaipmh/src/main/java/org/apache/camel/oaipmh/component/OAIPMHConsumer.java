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
package org.apache.camel.oaipmh.component;

import org.apache.camel.Processor;
import org.apache.camel.oaipmh.handler.ConsumerResponseHandler;
import org.apache.camel.oaipmh.handler.Harvester;
import org.apache.camel.support.DefaultScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The OAIPMH consumer.
 */
public class OAIPMHConsumer extends DefaultScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(OAIPMHConsumer.class);

    private Harvester harvester;

    public OAIPMHConsumer(OAIPMHEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.harvester = new Harvester(
                new ConsumerResponseHandler(this),
                endpoint.getUrl(),
                endpoint.getVerb(),
                endpoint.getMetadataPrefix(),
                endpoint.getUntil(),
                endpoint.getFrom(),
                endpoint.getSet(),
                endpoint.getIdentifier());
        if (endpoint.isIgnoreSSLWarnings()) {
            this.harvester.getHttpClient().setIgnoreSSLWarnings(true);
        }
    }

    @Override
    protected int poll() throws Exception {
        this.harvester.asynHarvest();
        return 0;
    }

}
