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
package org.apache.camel.component.camunda;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.camunda.internal.CamundaService;
import org.apache.camel.component.camunda.internal.OperationName;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Interact with Camunda 8 Orchestration Clusters using the Camunda Java Client.
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "camunda", title = "Camunda",
             syntax = "camunda:operationName",
             category = { Category.WORKFLOW, Category.SAAS },
             headersClass = CamundaConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   javaType = "org.apache.camel.component.camunda.model.JobWorkerMessage",
                                   byOption = @BodyType.When(option = "formatJSON", value = "true", carriers = Carrier.TEXT,
                                                             javaType = "java.lang.String"),
                                   description = "The job of the worker: a JobWorkerMessage with its key, type, custom"
                                                 + " headers and variables (formatJSON: the same as JSON text). The"
                                                 + " CamelCamundaJobKey header has the key of the job"),
           consumerReply = @BodyType(value = Carrier.ANY,
                                     description = "When the route ends the job is completed: a Map body gives its entries"
                                                   + " as the output variables, another body completes it without"
                                                   + " variables. A route that fails fails the job"),
           producerIn = @BodyType(value = {
                   Carrier.POJO, Carrier.TEXT, Carrier.MAP, Carrier.BYTES, Carrier.STREAM,
                   Carrier.NULL },
                                  description = "The request of the operation: a request object of"
                                                + " org.apache.camel.component.camunda.model, or the same as JSON text with"
                                                + " snake_case names. The job operations also take the JobWorkerMessage, or"
                                                + " with the CamelCamundaJobKey header a Map of the variables or no body;"
                                                + " deployResource takes text, bytes or a stream of the resource with the"
                                                + " CamelCamundaResourceName header. JSON as bytes or a stream fails: convert"
                                                + " it to text first"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = @BodyType.When(option = "formatJSON", value = "true", carriers = Carrier.TEXT,
                                                             javaType = "java.lang.String"),
                                   description = "The response of the operation: a ProcessResponse, MessageResponse,"
                                                 + " JobResponse or DeploymentResponse, with success false and the error when"
                                                 + " the call failed (formatJSON: the same as JSON text)"))
public class CamundaEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(label = "common", description = "The operation to use",
             enums = "startProcess,cancelProcess,publishMessage,completeJob,failJob,updateJobRetries,worker,throwError,deployResource")
    @Metadata(required = true)
    private OperationName operationName;

    @UriParam(defaultValue = "false")
    @Metadata(description = "Format the result in the body as JSON.")
    private boolean formatJSON;

    @UriParam
    @Metadata(label = "consumer", description = "Job type for the job worker.")
    private String jobType;

    @UriParam(defaultValue = "10")
    @Metadata(label = "consumer", description = "Timeout for job worker in seconds.")
    private int timeout = 10;

    public CamundaEndpoint() {
    }

    public CamundaEndpoint(String uri, CamundaComponent component, OperationName operationName) {
        super(uri, component);
        this.operationName = operationName;
    }

    @Override
    public String getServiceUrl() {
        if (getComponent().getClusterId() != null) {
            return getComponent().getClusterId() + "." + getComponent().getRegion();
        }
        return getComponent().getGrpcAddress();
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (getComponent().getClientId() != null) {
            return Map.of("clientId", getComponent().getClientId());
        }
        return null;
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(operationName, "operationName");
        if (operationName == OperationName.REGISTER_JOB_WORKER) {
            throw new IllegalArgumentException(
                    "Operation 'worker' is only supported as a consumer endpoint (from). "
                                               + "Use from(\"camunda://worker?...\") instead.");
        }
        return new CamundaProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(operationName, "operationName");
        if (operationName != OperationName.REGISTER_JOB_WORKER) {
            throw new IllegalArgumentException(
                    "Operation '" + operationName.value() + "' is only supported as a producer endpoint (to). "
                                               + "Use to(\"camunda://" + operationName.value() + "\") instead.");
        }

        Consumer consumer = new CamundaConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public void setOperationName(OperationName operationName) {
        this.operationName = operationName;
    }

    public OperationName getOperationName() {
        return operationName;
    }

    public void setFormatJSON(boolean formatJSON) {
        this.formatJSON = formatJSON;
    }

    public boolean isFormatJSON() {
        return formatJSON;
    }

    public String getJobType() {
        return jobType;
    }

    /**
     * The job type to register with the job worker
     */
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * The timeout in seconds used for job workers (default: 10)
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public CamundaComponent getComponent() {
        return (CamundaComponent) super.getComponent();
    }

    protected CamundaService getCamundaService() {
        return getComponent().getCamundaService();
    }
}
