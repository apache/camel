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
package org.apache.camel.component.alibaba.eventbridge;

import com.aliyun.eventbridge.EventBridgeClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeHeaders;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Publish events to Alibaba Cloud EventBridge.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-eventbridge", title = "Alibaba EventBridge",
             syntax = "alibaba-eventbridge:operation", category = { Category.CLOUD, Category.MESSAGING },
             headersClass = AlibabaEventBridgeHeaders.class, producerOnly = true)
public class AlibabaEventBridgeEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to perform", displayName = "Operation", label = "producer",
             enums = "putEvents")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Alibaba Cloud region", displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "EventBridge endpoint URL. Carries higher precedence than region based client initialization",
              displayName = "Endpoint")
    private String endpoint;

    @UriParam(description = "Access key for the cloud user", displayName = "Access Key",
              secret = true, security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "Secret Key",
              secret = true, security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Keys",
              secret = true, security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "Default event bus name", displayName = "Event Bus Name")
    private String eventBusName;

    @UriParam(description = "Default event source", displayName = "Event Source")
    private String eventSource;

    @UriParam(description = "Default event type", displayName = "Event Type")
    private String eventType;

    @UriParam(description = "Default event subject", displayName = "Event Subject")
    private String eventSubject;

    @UriParam(description = "Autowire an existing EventBridge client instance", displayName = "EventBridge Client",
              label = "advanced")
    @Metadata(autowired = true)
    private EventBridgeClient eventBridgeClient;

    private boolean autowiredEventBridgeClient;

    public AlibabaEventBridgeEndpoint() {
    }

    public AlibabaEventBridgeEndpoint(String uri, String operation, AlibabaEventBridgeComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AlibabaEventBridgeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public EventBridgeClient initClient() {
        if (eventBridgeClient != null) {
            return eventBridgeClient;
        }
        eventBridgeClient = AlibabaEventBridgeUtils.createClient(this);
        return eventBridgeClient;
    }

    @Override
    protected void doStop() throws Exception {
        if (eventBridgeClient != null && !autowiredEventBridgeClient) {
            eventBridgeClient = null;
        }
        super.doStop();
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public String getEventBusName() {
        return eventBusName;
    }

    public void setEventBusName(String eventBusName) {
        this.eventBusName = eventBusName;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventSubject() {
        return eventSubject;
    }

    public void setEventSubject(String eventSubject) {
        this.eventSubject = eventSubject;
    }

    public EventBridgeClient getEventBridgeClient() {
        return eventBridgeClient;
    }

    public void setEventBridgeClient(EventBridgeClient eventBridgeClient) {
        this.eventBridgeClient = eventBridgeClient;
        this.autowiredEventBridgeClient = eventBridgeClient != null;
    }
}
