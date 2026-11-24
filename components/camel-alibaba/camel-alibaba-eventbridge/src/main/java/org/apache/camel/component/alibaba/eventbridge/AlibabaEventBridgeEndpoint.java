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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  description = "The event to publish: the JSON text of the data of the event, as text, or as"
                                                + " bytes, a stream or a file read in the charset of the exchange (UTF-8 by"
                                                + " default), with the source, the type and the bus from the options or"
                                                + " headers; a Map of the event fields, such as eventSource, eventType and"
                                                + " eventData, where a Map or a POJO as data is serialized to JSON; a"
                                                + " CloudEvent of the Alibaba SDK; or a List of these for a batch. Another POJO"
                                                + " or no body fails"),
           producerOut = @BodyType(value = Carrier.MAP, javaType = "java.util.Map",
                                   description = "A Map with the requestId, the failedEntryCount, the resourceOwnerAccountId"
                                                 + " and the entryList of the response, with the request id in the"
                                                 + " CamelAlibabaEventBridgeRequestId header too. An event that EventBridge"
                                                 + " rejects does not fail the exchange: check failedEntryCount"))
public class AlibabaEventBridgeEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs. The CamelAlibabaEventBridgeOperation header replaces this"
                           + " option for a request that the producer sends. If that header is not set, an exchange property of"
                           + " the same name replaces this option.",
             displayName = "Operation", label = "producer", enums = "putEvents")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The region ID of EventBridge, for example cn-hangzhou. The producer sends it in the HTTP header"
                            + " x-eventbridge-regionId of each request. Not used when the eventBridgeClient option is set or"
                            + " autowired.",
              displayName = "Region")
    private String region;

    @UriParam(description = "The endpoint of EventBridge, which is a host name without http:// or https://. The region option"
                            + " does not determine the endpoint, so this option is required. Not used when the"
                            + " eventBridgeClient option is set or autowired.",
              displayName = "Endpoint")
    @Metadata(required = true)
    private String endpoint;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for the requests that the producer sends to EventBridge. If"
                            + " not set, the accessKey of the serviceKeys option is used, and one of the two is required. Not"
                            + " used when the eventBridgeClient option is set or autowired.",
              displayName = "Access Key", security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for the requests that the producer sends to"
                            + " EventBridge. If not set, the secretKey of the serviceKeys option is used, and one of the two is"
                            + " required. Not used when the eventBridgeClient option is set or autowired.",
              displayName = "Secret Key", security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys. Its"
                            + " accessKey is the AccessKey ID and its secretKey is the AccessKey secret. Each of the two is"
                            + " used when the option of the same name is not set. Not used when the eventBridgeClient option is"
                            + " set or autowired.",
              displayName = "Service Keys", security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "The name of the event bus for a JSON text or Map body. The CamelAlibabaEventBridgeEventBusName"
                            + " header replaces this option for a request that the producer sends. If that header is not set,"
                            + " an exchange property of the same name replaces this option. The key eventBusName of a Map body"
                            + " replaces the three. One of the three is required for a JSON text body, or a Map body without"
                            + " that key.",
              displayName = "Event Bus Name")
    private String eventBusName;

    @UriParam(description = "The event source for a JSON text or Map body. The value must be a URI. The"
                            + " CamelAlibabaEventBridgeEventSource header replaces this option for a request that the producer"
                            + " sends. If that header is not set, an exchange property of the same name replaces this option."
                            + " The key eventSource or source of a Map body replaces the three. One of the three is required"
                            + " for a JSON text body, or a Map body without that key.",
              displayName = "Event Source")
    private String eventSource;

    @UriParam(description = "The event type for a JSON text or Map body. The CamelAlibabaEventBridgeEventType header replaces"
                            + " this option for a request that the producer sends. If that header is not set, an exchange"
                            + " property of the same name replaces this option. The key eventType or type of a Map body"
                            + " replaces the three. One of the three is required for a JSON text body, or a Map body without"
                            + " that key.",
              displayName = "Event Type")
    private String eventType;

    @UriParam(description = "The event subject for a JSON text or Map body. The CamelAlibabaEventBridgeEventSubject header"
                            + " replaces this option for a request that the producer sends. If that header is not set, an"
                            + " exchange property of the same name replaces this option. The key eventSubject or subject of a"
                            + " Map body replaces the three. If neither the three nor that key is set, the producer adds no"
                            + " event subject to the event.",
              displayName = "Event Subject")
    private String eventSubject;

    @UriParam(description = "Whether to check that the event bus exists and has a rule that matches the event source. The"
                            + " producer calls listEventBuses and listRules to check this. The producer also checks that each"
                            + " event source that allowedEventSources lists for the event bus matches a rule. The"
                            + " CamelAlibabaEventBridgeValidateEventSource header replaces this option for a request that the"
                            + " producer sends. If that header is not set, an exchange property of the same name replaces this"
                            + " option.",
              displayName = "Validate Event Source", label = "producer", defaultValue = "false")
    private boolean validateEventSource;

    @UriParam(description = "Whether to check that the event bus exists and has a rule that matches the event source and event"
                            + " type. The producer calls listEventBuses and listRules to check this. The producer also checks"
                            + " that each event type that allowedEventSources lists for the event bus matches a rule. The"
                            + " CamelAlibabaEventBridgeValidateEventType header replaces this option for a request that the"
                            + " producer sends. If that header is not set, an exchange property of the same name replaces this"
                            + " option.",
              displayName = "Validate Event Type", label = "producer", defaultValue = "false")
    private boolean validateEventType;

    @UriParam(description = "Whether to check the keys specversion and id of a Map body. The value of specversion must be 1.0,"
                            + " and the value of id must not be blank. The CamelAlibabaEventBridgeValidateEventSpec header"
                            + " replaces this option for a request that the producer sends. If that header is not set, an"
                            + " exchange property of the same name replaces this option.",
              displayName = "Validate Event Spec", label = "producer", defaultValue = "true")
    private boolean validateEventSpec = true;

    @UriParam(description = "The event sources and event types that the producer allows on each event bus. Written as JSON or"
                            + " as bus blocks in the form bus1[source1 = type1,type2] | bus2[source2 = type3]. Semicolons"
                            + " separate the event sources of one event bus. A value in the form source1 = type1,type2 applies"
                            + " to the event bus that eventBusName sets for the exchange. If eventBusName is not set, it"
                            + " applies to every event bus. The CamelAlibabaEventBridgeAllowedEventSources header replaces this"
                            + " option for a request that the producer sends. If that header is not set, an exchange property"
                            + " of the same name replaces this option. If none of the three is set, the producer allows every"
                            + " event bus, event source and event type.",
              displayName = "Allowed Event Sources", label = "producer")
    private String allowedEventSources;

    @UriParam(description = "The time in milliseconds for which the producer caches the result of listEventBuses and listRules"
                            + " for an event bus. Only used when validateEventSource or validateEventType is true. Use 0 or"
                            + " less to cache nothing. The CamelAlibabaEventBridgeEventSourceCacheTtl header replaces this"
                            + " option for a request that the producer sends. If that header is not set, an exchange property"
                            + " of the same name replaces this option.",
              displayName = "Event Source Cache TTL", label = "producer,advanced", defaultValue = "300000")
    private long eventSourceCacheTtl = 300000L;

    @UriParam(description = "To use a custom com.aliyun.eventbridge.EventBridgeClient. The producer sends its requests with"
                            + " this client, and the region, endpoint, accessKey, secretKey and serviceKeys options are not"
                            + " used. If no client is set or autowired, the Camel endpoint creates an EventBridgeClient from"
                            + " these options. It creates this client when the producer processes its first message.",
              displayName = "EventBridge Client", label = "advanced")
    @Metadata(autowired = true)
    private volatile EventBridgeClient eventBridgeClient;

    private boolean autowiredEventBridgeClient;
    private EventSourceCache eventSourceCache;

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
        EventBridgeClient answer = eventBridgeClient;
        if (answer == null) {
            lock.lock();
            try {
                answer = eventBridgeClient;
                if (answer == null) {
                    answer = createClient();
                    eventBridgeClient = answer;
                }
            } finally {
                lock.unlock();
            }
        }
        return answer;
    }

    EventBridgeClient createClient() {
        return AlibabaEventBridgeUtils.createClient(this);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (eventSourceCache == null) {
            eventSourceCache = new EventSourceCache(eventSourceCacheTtl);
        }
    }

    @Override
    protected void doStop() throws Exception {
        lock.lock();
        try {
            if (eventBridgeClient != null && !autowiredEventBridgeClient) {
                eventBridgeClient = null;
            }
        } finally {
            lock.unlock();
        }
        if (eventSourceCache != null) {
            eventSourceCache.clear();
            eventSourceCache = null;
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

    public boolean isValidateEventSource() {
        return validateEventSource;
    }

    public void setValidateEventSource(boolean validateEventSource) {
        this.validateEventSource = validateEventSource;
    }

    public boolean isValidateEventType() {
        return validateEventType;
    }

    public void setValidateEventType(boolean validateEventType) {
        this.validateEventType = validateEventType;
    }

    public boolean isValidateEventSpec() {
        return validateEventSpec;
    }

    public void setValidateEventSpec(boolean validateEventSpec) {
        this.validateEventSpec = validateEventSpec;
    }

    public String getAllowedEventSources() {
        return allowedEventSources;
    }

    public void setAllowedEventSources(String allowedEventSources) {
        this.allowedEventSources = allowedEventSources;
    }

    public long getEventSourceCacheTtl() {
        return eventSourceCacheTtl;
    }

    public void setEventSourceCacheTtl(long eventSourceCacheTtl) {
        this.eventSourceCacheTtl = eventSourceCacheTtl;
    }

    public EventSourceCache getEventSourceCache() {
        return eventSourceCache;
    }

    public void setEventSourceCache(EventSourceCache eventSourceCache) {
        this.eventSourceCache = eventSourceCache;
    }

    public EventBridgeClient getEventBridgeClient() {
        return eventBridgeClient;
    }

    public void setEventBridgeClient(EventBridgeClient eventBridgeClient) {
        lock.lock();
        try {
            this.eventBridgeClient = eventBridgeClient;
            this.autowiredEventBridgeClient = eventBridgeClient != null;
        } finally {
            lock.unlock();
        }
    }
}
