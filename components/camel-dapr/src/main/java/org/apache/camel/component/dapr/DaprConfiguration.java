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
package org.apache.camel.component.dapr;

import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.HttpExtension;
import io.dapr.client.domain.StateOptions.Concurrency;
import io.dapr.client.domain.StateOptions.Consistency;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DaprConfiguration implements Cloneable {

    @UriPath(label = "producer", enums = "invokeService, state")
    @Metadata(required = true)
    private DaprOperation operation;
    @UriParam(label = "producer", description = "Target service to invoke. Can be a Dapr App ID, a named HTTPEndpoint, " +
                                                "or a FQDN/public URL")
    private String serviceToInvoke;
    @UriParam(label = "producer", description = "The name of the method or route to invoke on the target service")
    private String methodToInvoke;
    @UriParam(label = "producer", description = "The HTTP verb to use for invoking the method", defaultValue = "POST")
    private String verb = "POST";
    @UriParam(label = "producer",
              description = "HTTP method to use when invoking the service. Accepts verbs like GET, POST, PUT, DELETE, etc. "
                            + "Creates a minimal HttpExtension with no headers or query params. Takes precedence over verb")
    @Metadata(autowired = true)
    private HttpExtension httpExtension;
    @UriParam(label = "producer", enums = "save, saveBulk, get, getBulk, delete, executeTransaction", defaultValue = "get",
              description = "The state operation to perform on the state store. Required for DaprOperation.state operation")
    private StateOperation stateOperation = StateOperation.get;
    @UriParam(label = "producer",
              description = "The name of the Dapr state store to interact with, defined in statestore.yaml config")
    private String stateStore;
    @UriParam(label = "producer", description = "The key used to identify the state object within the specified state store")
    private String key;
    @UriParam(label = "producer", description = "The eTag for optimistic concurrency during state save or delete operations")
    private String eTag;
    @UriParam(label = "producer", description = "Concurrency mode to use with state operations",
              enums = "FIRST_WRITE, LAST_WRITE")
    private Concurrency concurrency;
    @UriParam(label = "producer", description = "Consistency level to use with state operations", enums = "EVENTUAL, STRONG")
    private Consistency consistency;
    @UriParam(label = "consumer", description = "The client to consume messages by the consumer")
    @Metadata(autowired = true)
    private DaprPreviewClient previewClient;
    @UriParam(label = "common", description = "The name of the Dapr Pub/Sub component to use. This identifies which underlying "
                                              + "messaging system Dapr will interact with for publishing or subscribing to events.")
    private String pubSubName;
    @UriParam(label = "common", description = "The name of the topic to subscribe to. The topic must exist in the Pub/Sub "
                                              + "component configured under the given pubsubName.")
    private String topic;
    @UriParam(label = "common", description = "The contentType for the Pub/Sub component to use.")
    private String contentType;
    @UriParam(label = "producer", description = "The name of the Dapr binding to invoke")
    private String bindingName;
    @UriParam(label = "producer", description = "The operation to perform on the binding")
    private String bindingOperation;

    /**
     * The Dapr <b>building block operation</b> to perform with this component
     */
    public DaprOperation getOperation() {
        return operation;
    }

    public void setOperation(DaprOperation operation) {
        this.operation = operation;
    }

    /**
     * The <b>target service</b> to invoke.
     * <p>
     * This can be one of the following:
     * <ul>
     * <li>A Dapr App ID</li>
     * <li>A named HTTPEndpoint resource</li>
     * <li>A fully qualified domain name (FQDN) or public URL</li>
     * </ul>
     */
    public String getServiceToInvoke() {
        return serviceToInvoke;
    }

    public void setServiceToInvoke(String serviceToInvoke) {
        this.serviceToInvoke = serviceToInvoke;
    }

    /**
     * The <b>method or route</b> to invoke on the target service.
     * <p>
     * This defines the specific method or endpoint to invoke on the target service, such as a method name or route
     * path.
     */
    public String getMethodToInvoke() {
        return methodToInvoke;
    }

    public void setMethodToInvoke(String methodToInvoke) {
        this.methodToInvoke = methodToInvoke;
    }

    /**
     * The <b>HTTP verb</b> to use for service invocation.
     * <p>
     * This defines the type of HTTP request to send when invoking the service method. Defaults to POST.
     */
    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    /**
     * The <b>HttpExtension</b> to use for service invocation.
     * <p>
     * Minimal HttpExtension object without query parameters and headers. Takes precedence over defined verb, query
     * parameter and headers.
     */
    public HttpExtension getHttpExtension() {
        return httpExtension;
    }

    public void setHttpExtension(HttpExtension httpExtension) {
        this.httpExtension = httpExtension;
    }

    /**
     * The <b>state operation</b> to perform on the state store. enums: save, saveBulk, get, getBulk, delete,
     * executeTransaction
     */
    public StateOperation getStateOperation() {
        return stateOperation;
    }

    public void setStateOperation(StateOperation stateOperation) {
        this.stateOperation = stateOperation;
    }

    /**
     * The name of the Dapr <b>state store</b> to interact with.
     * <p>
     * Required for all state management operations.
     */
    public String getStateStore() {
        return stateStore;
    }

    public void setStateStore(String stateStore) {
        this.stateStore = stateStore;
    }

    /**
     * The key used to identify the <b>state object</b> within the specified state store.
     * <p>
     * Required for all state management operations.
     */
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /**
     * ETag used for <b>optimistic concurrency</b> during state save or delete operations.
     * <p>
     * Ensures the operation is applied only if the ETag matches the current state version.
     */
    public String getETag() {
        return eTag;
    }

    public void setETag(String eTag) {
        this.eTag = eTag;
    }

    /**
     * <b>Concurrency</b> mode to use with state operations.
     * <p>
     * 'FIRST_WRITE' enforces that only the first write succeeds 'LAST_WRITE' allows the latest write to overwrite
     * previous versions
     */
    public Concurrency getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(Concurrency concurrency) {
        this.concurrency = concurrency;
    }

    /**
     * <b>Consistency</b> level to use with state operations.
     * <p>
     * 'EVENTUAL' allows for faster, potentially out-of-order writes 'STRONG' ensures writes are immediately visible and
     * consistent
     */
    public Consistency getConsistency() {
        return consistency;
    }

    public void setConsistency(Consistency consistency) {
        this.consistency = consistency;
    }

    /**
     * The name of the Dapr <b>Pub/Sub component</b> to use.
     *
     * <p>
     * This identifies which underlying messaging system Dapr will interact with for publishing or subscribing to
     * events.
     */
    public String getPubSubName() {
        return pubSubName;
    }

    public void setPubSubName(String pubSubName) {
        this.pubSubName = pubSubName;
    }

    /**
     * The name of the <b>topic</b> to subscribe to.
     *
     * <p>
     * The topic must exist in the Pub/Sub component configured under the given pubsubName.
     */
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * The <b>content type</b> for the Pub/Sub component to use.
     */
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * The <b>preview client</b> to consume messages by the consumer.
     */
    public DaprPreviewClient getPreviewClient() {
        return previewClient;
    }

    public void setPreviewClient(DaprPreviewClient previewClient) {
        this.previewClient = previewClient;
    }

    /**
     * The <b>name</b> of the Dapr binding to invoke.
     */
    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }

    /**
     * The <b>operation</b> to perform on the binding.
     */
    public String getBindingOperation() {
        return bindingOperation;
    }

    public void setBindingOperation(String bindingOperation) {
        this.bindingOperation = bindingOperation;
    }

    public DaprConfiguration copy() {
        try {
            return (DaprConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
