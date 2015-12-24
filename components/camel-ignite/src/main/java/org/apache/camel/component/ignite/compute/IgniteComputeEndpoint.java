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
package org.apache.camel.component.ignite.compute;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ignite.AbstractIgniteEndpoint;
import org.apache.camel.component.ignite.ClusterGroupExpression;
import org.apache.camel.component.ignite.IgniteComponent;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCompute;

/**
 * Ignite Compute endpoint.
 */
@UriEndpoint(scheme = "ignite:compute", title = "Ignite Compute", syntax = "ignite:compute:[endpointId]", label = "nosql,cache,compute", producerOnly = true)
public class IgniteComputeEndpoint extends AbstractIgniteEndpoint {

    @UriParam
    private ClusterGroupExpression clusterGroupExpression;

    @UriParam
    private IgniteComputeExecutionType executionType;

    @UriParam
    private String taskName;

    @UriParam
    private String computeName;

    @UriParam
    private Long timeoutMillis;

    public IgniteComputeEndpoint(String uri, URI remainingUri, Map<String, Object> parameters, IgniteComponent igniteComponent) throws ClassNotFoundException {
        super(uri, igniteComponent);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new IgniteComputeProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The Ignite Compute endpoint does not support consumers.");
    }

    public IgniteCompute createIgniteCompute() {
        Ignite ignite = ignite();
        IgniteCompute compute = clusterGroupExpression == null ? ignite.compute() : ignite.compute(clusterGroupExpression.getClusterGroup(ignite));

        if (computeName != null) {
            compute = compute.withName(computeName);
        }

        if (timeoutMillis != null) {
            compute = compute.withTimeout(timeoutMillis);
        }

        return compute;
    }

    /**
     * Gets the execution type of this producer.
     * 
     * @return
     */
    public IgniteComputeExecutionType getExecutionType() {
        return executionType;
    }

    /**
     * Sets the execution type of this producer.
     * 
     * @param executionType
     */
    public void setExecutionType(IgniteComputeExecutionType executionType) {
        this.executionType = executionType;
    }

    /**
     * Gets the task name, only applicable if using the {@link IgniteComputeExecutionType#EXECUTE} execution type.
     * 
     * @return
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * Sets the task name, only applicable if using the {@link IgniteComputeExecutionType#EXECUTE} execution type.
     * 
     * @param taskName
     */
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    /**
     * Gets the name of the compute job, which will be set via {@link IgniteCompute#withName(String)}. 
     * 
     * @return
     */
    public String getComputeName() {
        return computeName;
    }

    /**
     * Sets the name of the compute job, which will be set via {@link IgniteCompute#withName(String)}.
     * 
     * @param computeName
     */
    public void setComputeName(String computeName) {
        this.computeName = computeName;
    }

    /**
     * Gets the timeout interval for triggered jobs, in milliseconds, which will be set via {@link IgniteCompute#withTimeout(long)}.
     * 
     * @return
     */
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Sets the timeout interval for triggered jobs, in milliseconds, which will be set via {@link IgniteCompute#withTimeout(long)}.
     * 
     * @param timeoutMillis
     */
    public void setTimeoutMillis(Long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

}
