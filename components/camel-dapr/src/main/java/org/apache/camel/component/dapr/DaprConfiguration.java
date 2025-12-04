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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.HttpExtension;
import io.dapr.client.domain.StateOptions.Concurrency;
import io.dapr.client.domain.StateOptions.Consistency;
import io.dapr.workflows.client.DaprWorkflowClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DaprConfiguration implements Cloneable {

    @UriPath(
            label = "producer",
            enums = "invokeService, state, pubSub, invokeBinding, secret, configuration, lock, workflow")
    @Metadata(required = true)
    private DaprOperation operation;

    @UriParam(label = "common", description = "The Dapr Client")
    @Metadata(autowired = true)
    private DaprClient client;

    @UriParam(label = "common", description = "The Dapr Preview Client")
    @Metadata(autowired = true)
    private DaprPreviewClient previewClient;

    @UriParam(label = "producer", description = "The Dapr Workflow Client")
    @Metadata(autowired = true)
    private DaprWorkflowClient workflowClient;

    @UriParam(
            label = "producer",
            description =
                    "Target service to invoke. Can be a Dapr App ID, a named HTTPEndpoint, " + "or a FQDN/public URL")
    private String serviceToInvoke;

    @UriParam(label = "producer", description = "The name of the method or route to invoke on the target service")
    private String methodToInvoke;

    @UriParam(label = "producer", description = "The HTTP verb to use for invoking the method", defaultValue = "POST")
    private String verb = "POST";

    @UriParam(
            label = "producer",
            description =
                    "HTTP method to use when invoking the service. Accepts verbs like GET, POST, PUT, DELETE, etc. "
                            + "Creates a minimal HttpExtension with no headers or query params. Takes precedence over verb")
    @Metadata(autowired = true)
    private HttpExtension httpExtension;

    @UriParam(
            label = "producer",
            enums = "save, saveBulk, get, getBulk, delete, executeTransaction",
            defaultValue = "get",
            description =
                    "The state operation to perform on the state store. Required for DaprOperation.state operation")
    private StateOperation stateOperation = StateOperation.get;

    @UriParam(
            label = "producer",
            description = "The name of the Dapr state store to interact with, defined in statestore.yaml config")
    private String stateStore;

    @UriParam(
            label = "producer",
            description =
                    "The name of the Dapr secret store to interact with, defined in local-secret-store.yaml config")
    private String secretStore;

    @UriParam(
            label = "common",
            description =
                    "The name of the Dapr configuration store to interact with, defined in statestore.yaml config")
    private String configStore;

    @UriParam(
            label = "producer",
            description = "The key used to identify the state/secret object within the specified state/secret store")
    private String key;

    @UriParam(
            label = "producer",
            description = "The eTag for optimistic concurrency during state save or delete operations")
    private String eTag;

    @UriParam(
            label = "producer",
            description = "Concurrency mode to use with state operations",
            enums = "FIRST_WRITE, LAST_WRITE")
    private Concurrency concurrency;

    @UriParam(
            label = "producer",
            description = "Consistency level to use with state operations",
            enums = "EVENTUAL, STRONG")
    private Consistency consistency;

    @UriParam(
            label = "common",
            description = "The name of the Dapr Pub/Sub component to use. This identifies which underlying "
                    + "messaging system Dapr will interact with for publishing or subscribing to events.")
    private String pubSubName;

    @UriParam(
            label = "common",
            description = "The name of the topic to subscribe to. The topic must exist in the Pub/Sub "
                    + "component configured under the given pubsubName.")
    private String topic;

    @UriParam(label = "common", description = "The contentType for the Pub/Sub component to use.")
    private String contentType;

    @UriParam(label = "producer", description = "The name of the Dapr binding to invoke")
    private String bindingName;

    @UriParam(label = "producer", description = "The operation to perform on the binding")
    private String bindingOperation;

    @UriParam(label = "common", description = "List of keys for configuration operation")
    private String configKeys;

    @UriParam(
            label = "producer",
            enums = "tryLock, unlock",
            defaultValue = "tryLock",
            description = "The lock operation to perform on the store. Required for DaprOperation.lock operation")
    private LockOperation lockOperation = LockOperation.tryLock;

    @UriParam(label = "producer", description = "The lock store name")
    private String storeName;

    @UriParam(label = "producer", description = "The resource Id for the lock")
    private String resourceId;

    @UriParam(label = "producer", description = "The lock owner identifier for the lock")
    private String lockOwner;

    @UriParam(label = "producer", description = "The expiry time in seconds for the lock")
    private Integer expiryInSeconds;

    @UriParam(
            label = "producer",
            enums =
                    "scheduleNew, terminate, purge, suspend, resume, state, waitForInstanceStart, waitForInstanceCompletion, raiseEvent",
            defaultValue = "scheduleNew",
            description = "The workflow operation to perform. Required for DaprOperation.workflow operation")
    private WorkflowOperation workflowOperation = WorkflowOperation.scheduleNew;

    @UriParam(label = "producer", description = "The FQCN of the class which implements io.dapr.workflows.Workflow")
    private String workflowClass;

    @UriParam(label = "producer", description = "The version of the workflow to start")
    private String workflowVersion;

    @UriParam(label = "producer", description = "The instance ID of the workflow")
    private String workflowInstanceId;

    @UriParam(label = "producer", description = "The start time of the new workflow")
    private Instant workflowStartTime;

    @UriParam(label = "producer", description = "Reason for suspending/resuming the workflow instance")
    private String reason;

    @UriParam(
            label = "producer",
            description =
                    "Set true to fetch the workflow instance's inputs, outputs, and custom status, or false to omit")
    private boolean getWorkflowIO;

    @UriParam(
            label = "producer",
            description = "The amount of time to wait for the workflow instance to start/complete")
    private Duration timeout;

    @UriParam(label = "producer", description = "The name of the event. Event names are case-insensitive")
    private String eventName;

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
     * The <b>client</b>
     */
    public DaprClient getClient() {
        return client;
    }

    public void setClient(DaprClient client) {
        this.client = client;
    }

    /**
     * The <b>preview client</b>
     */
    public DaprPreviewClient getPreviewClient() {
        return previewClient;
    }

    public void setPreviewClient(DaprPreviewClient previewClient) {
        this.previewClient = previewClient;
    }

    /**
     * The <b>preview client</b>
     */
    public DaprWorkflowClient getWorkflowClient() {
        return workflowClient;
    }

    public void setWorkflowClient(DaprWorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
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
     * The name of the Dapr <b>secret store</b> to interact with.
     * <p>
     * Required for all secret management operations.
     */
    public String getSecretStore() {
        return secretStore;
    }

    public void setSecretStore(String secretStore) {
        this.secretStore = secretStore;
    }

    /**
     * The name of the Dapr <b>configuration store</b> to interact with.
     * <p>
     * Required for all configuration management operations.
     */
    public String getConfigStore() {
        return configStore;
    }

    public void setConfigStore(String configStore) {
        this.configStore = configStore;
    }

    /**
     * The key used to identify the <b>state/secret object</b> within the specified state/secret store.
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

    /**
     * Comma separated list of <b>keys</b> for configuration operation.
     */
    public String getConfigKeys() {
        return configKeys;
    }

    public List<String> getConfigKeysAsList() {
        if (configKeys != null) {
            return List.of(configKeys.split(","));
        } else {
            return null;
        }
    }

    public void setConfigKeys(String configKeys) {
        this.configKeys = configKeys;
    }

    /**
     * The <b>lock operation</b> to perform on the store. Required for DaprOperation.lock operation
     */
    public LockOperation getLockOperation() {
        return lockOperation;
    }

    public void setLockOperation(LockOperation lockOperation) {
        this.lockOperation = lockOperation;
    }

    /**
     * The lock <b>store name</b>
     */
    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    /**
     * The <b>resource Id</b> for the lock
     */
    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * The <b>lock owner</b> identifier for the lock
     */
    public String getLockOwner() {
        return lockOwner;
    }

    public void setLockOwner(String lockOwner) {
        this.lockOwner = lockOwner;
    }

    /**
     * The <b>expiry time</b> in seconds for the lock
     */
    public Integer getExpiryInSeconds() {
        return expiryInSeconds;
    }

    public void setExpiryInSeconds(Integer expiryInSeconds) {
        this.expiryInSeconds = expiryInSeconds;
    }

    /**
     * The <b>workflow operation</b> to perform. Required for DaprOperation.workflow operation
     */
    public WorkflowOperation getWorkflowOperation() {
        return workflowOperation;
    }

    public void setWorkflowOperation(WorkflowOperation workflowOperation) {
        this.workflowOperation = workflowOperation;
    }

    /**
     * The FQCN of the <b>class</b> which implements io.dapr.workflows.Workflow
     */
    public String getWorkflowClass() {
        return workflowClass;
    }

    public void setWorkflowClass(String workflowClass) {
        this.workflowClass = workflowClass;
    }

    /**
     * The <b>version</b> of the workflow to start
     */
    public String getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(String workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    /**
     * The <b>instance ID</b> of the workflow to start
     */
    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    /**
     * The <b>start time</b> of the new workflow
     */
    public Instant getWorkflowStartTime() {
        return workflowStartTime;
    }

    public void setWorkflowStartTime(Instant workflowStartTime) {
        this.workflowStartTime = workflowStartTime;
    }

    /**
     * <b>Reason</b> for suspending/resuming the workflow instance
     */
    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Set <b>true</b> to fetch the workflow instance's inputs, outputs, and custom status, or <b>false</b> to omit
     */
    public boolean isGetWorkflowIO() {
        return getWorkflowIO;
    }

    public void setGetWorkflowIO(boolean getWorkflowIO) {
        this.getWorkflowIO = getWorkflowIO;
    }

    /**
     * The amount of <b>time to wait for the workflow instance to start/complete
     */
    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * The name of the <b>event</b>. Event names are case-insensitive
     */
    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public DaprConfiguration copy() {
        try {
            return (DaprConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
