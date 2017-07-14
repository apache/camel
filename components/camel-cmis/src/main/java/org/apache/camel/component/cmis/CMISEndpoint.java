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

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The cmis component uses the Apache Chemistry client API and allows you to add/read nodes to/from a CMIS compliant content repositories.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "cmis", title = "CMIS", syntax = "cmis:cmsUrl", consumerClass = CMISConsumer.class, label = "cms,database")
public class CMISEndpoint extends DefaultEndpoint {

    @UriPath(description = "URL to the cmis repository")
    @Metadata(required = "true")
    private final String cmsUrl;

    @UriParam(label = "producer")
    private boolean queryMode;

    @UriParam
    private CMISSessionFacade sessionFacade; // to include in component documentation

    @UriParam(label = "advanced")
    private CMISSessionFacadeFactory sessionFacadeFactory;

    private Map<String, Object> properties; // properties for each session facade instance being created

    public CMISEndpoint(String uri, CMISComponent component, String cmsUrl) {
        this(uri, component, cmsUrl, new DefaultCMISSessionFacadeFactory());
    }

    public CMISEndpoint(String uri, CMISComponent component, String cmsUrl, CMISSessionFacadeFactory sessionFacadeFactory) {
        super(uri, component);
        this.cmsUrl = cmsUrl;
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

    public String getCmsUrl() {
        return cmsUrl;
    }

    public CMISSessionFacade getSessionFacade() {
        return sessionFacade;
    }

    /**
     * Session configuration
     */
    public void setSessionFacade(CMISSessionFacade sessionFacade) {
        this.sessionFacade = sessionFacade;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public CMISSessionFacadeFactory getSessionFacadeFactory() {
        return sessionFacadeFactory;
    }

    /**
     * To use a custom CMISSessionFacadeFactory to create the CMISSessionFacade instances
     */
    public void setSessionFacadeFactory(CMISSessionFacadeFactory sessionFacadeFactory) {
        this.sessionFacadeFactory = sessionFacadeFactory;
    }
}
