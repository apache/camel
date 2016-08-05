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
package org.apache.camel.component.cmis;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cmis component uses the Apache Chemistry client API and allows you to add/read nodes to/from a CMIS compliant content repositories.
 */
@UriEndpoint(scheme = "cmis", title = "CMIS", syntax = "cmis:url", consumerClass = CMISConsumer.class, label = "cms,database")
public class CMISEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(CMISEndpoint.class);

    private final CMISSessionFacadeFactory sessionFacadeFactory;

    @UriPath(description = "the cmis url")
    @Metadata(required = "true")
    private final String url;

    @UriParam(label = "producer")
    private boolean queryMode;

    public CMISEndpoint(String uri, CMISComponent cmisComponent, CMISSessionFacadeFactory sessionFacadeFactory) {
        super(uri, cmisComponent);

        this.url = uri;
        this.sessionFacadeFactory = sessionFacadeFactory;
    }

    @Override
    public Producer createProducer() throws Exception {
        return this.queryMode
            ? new CMISQueryProducer(this, sessionFacadeFactory)
            : new CMISProducer(this, sessionFacadeFactory);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        CMISConsumer consumer = new CMISConsumer(this, processor, sessionFacadeFactory);
        configureConsumer(consumer);

        return consumer;
    }

    public boolean isSingleton() {
        return true;
    }

    public boolean isQueryMode() {
        return queryMode;
    }

    /**
     * If true, will execute the cmis query from the message body and return result, otherwise will create a node in the cmis repository
     */
    public void setQueryMode(boolean queryMode) {
        this.queryMode = queryMode;
    }
}
