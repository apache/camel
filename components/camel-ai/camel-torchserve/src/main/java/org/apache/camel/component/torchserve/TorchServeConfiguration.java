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
package org.apache.camel.component.torchserve;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.torchserve.client.model.RegisterOptions;
import org.apache.camel.component.torchserve.client.model.ScaleWorkerOptions;
import org.apache.camel.component.torchserve.client.model.UnregisterOptions;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
@Configurer
public class TorchServeConfiguration implements Cloneable {

    @UriParam(label = "inference,security")
    private String inferenceKey;

    @UriParam(label = "inference")
    private String inferenceAddress;

    @UriParam(label = "inference", defaultValue = "8080")
    private int inferencePort = 8080;

    @UriParam(label = "management,security")
    private String managementKey;

    @UriParam(label = "management")
    private String managementAddress;

    @UriParam(label = "management", defaultValue = "8081")
    private int managementPort = 8081;

    @UriParam(label = "metrics")
    private String metricsAddress;

    @UriParam(label = "metrics", defaultValue = "8082")
    private int metricsPort = 8082;

    @UriParam(label = "common")
    private String modelName;

    @UriParam(label = "common")
    private String modelVersion;

    @UriParam(label = "management")
    private String url;

    @UriParam(label = "management")
    private RegisterOptions registerOptions;

    @UriParam(label = "management")
    private ScaleWorkerOptions scaleWorkerOptions;

    @UriParam(label = "management")
    private UnregisterOptions unregisterOptions;

    @UriParam(label = "management", defaultValue = "100")
    private int listLimit = 100;

    @UriParam(label = "management")
    private String listNextPageToken;

    @UriParam(label = "metrics")
    private String metricsName;

    public String getInferenceKey() {
        return inferenceKey;
    }

    /**
     * The token authorization key for accessing the inference API.
     */
    public void setInferenceKey(String inferenceKey) {
        this.inferenceKey = inferenceKey;
    }

    public String getInferenceAddress() {
        return inferenceAddress;
    }

    /**
     * The address of the inference API endpoint.
     */
    public void setInferenceAddress(String inferenceAddress) {
        this.inferenceAddress = inferenceAddress;
    }

    public int getInferencePort() {
        return inferencePort;
    }

    /**
     * The port of the inference API endpoint.
     */
    public void setInferencePort(int inferencePort) {
        this.inferencePort = inferencePort;
    }

    public String getManagementKey() {
        return managementKey;
    }

    /**
     * The token authorization key for accessing the management API.
     */
    public void setManagementKey(String managementKey) {
        this.managementKey = managementKey;
    }

    public String getManagementAddress() {
        return managementAddress;
    }

    /**
     * The address of the management API endpoint.
     */
    public void setManagementAddress(String managementAddress) {
        this.managementAddress = managementAddress;
    }

    public int getManagementPort() {
        return managementPort;
    }

    /**
     * The port of the management API endpoint.
     */
    public void setManagementPort(int managementPort) {
        this.managementPort = managementPort;
    }

    public String getMetricsAddress() {
        return metricsAddress;
    }

    /**
     * The address of the metrics API endpoint.
     */
    public void setMetricsAddress(String metricsAddress) {
        this.metricsAddress = metricsAddress;
    }

    public int getMetricsPort() {
        return metricsPort;
    }

    /**
     * The port of the metrics API endpoint.
     */
    public void setMetricsPort(int metricsPort) {
        this.metricsPort = metricsPort;
    }

    public String getModelName() {
        return modelName;
    }

    /**
     * The name of model.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    /**
     * The version of model.
     */
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Model archive download url, support local file or HTTP(s) protocol. For S3, consider using pre-signed url.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    public RegisterOptions getRegisterOptions() {
        return registerOptions;
    }

    /**
     * Additional options for the register operation.
     */
    public void setRegisterOptions(RegisterOptions registerOptions) {
        this.registerOptions = registerOptions;
    }

    public ScaleWorkerOptions getScaleWorkerOptions() {
        return scaleWorkerOptions;
    }

    /**
     * Additional options for the scale-worker operation.
     */
    public void setScaleWorkerOptions(ScaleWorkerOptions scaleWorkerOptions) {
        this.scaleWorkerOptions = scaleWorkerOptions;
    }

    public UnregisterOptions getUnregisterOptions() {
        return unregisterOptions;
    }

    /**
     * Additional options for the unregister operation.
     */
    public void setUnregisterOptions(UnregisterOptions unregisterOptions) {
        this.unregisterOptions = unregisterOptions;
    }

    public int getListLimit() {
        return listLimit;
    }

    /**
     * The maximum number of items to return for the list operation. When this value is present, TorchServe does not
     * return more than the specified number of items, but it might return fewer. This value is optional. If you include
     * a value, it must be between 1 and 1000, inclusive. If you do not include a value, it defaults to 100.
     */
    public void setListLimit(int listLimit) {
        this.listLimit = listLimit;
    }

    public String getListNextPageToken() {
        return listNextPageToken;
    }

    /**
     * The token to retrieve the next set of results for the list operation. TorchServe provides the token when the
     * response from a previous call has more results than the maximum page size.
     */
    public void setListNextPageToken(String listNextPageToken) {
        this.listNextPageToken = listNextPageToken;
    }

    public String getMetricsName() {
        return metricsName;
    }

    /**
     * Names of metrics to filter.
     */
    public void setMetricsName(String metricsName) {
        this.metricsName = metricsName;
    }

    public TorchServeConfiguration copy() {
        try {
            return (TorchServeConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
