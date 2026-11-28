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
package org.apache.camel.component.tensorflow.serving;

import io.grpc.ChannelCredentials;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
@Configurer
public class TensorFlowServingConfiguration implements Cloneable {

    @UriParam(label = "common", defaultValue = "localhost:8500",
              description = "The target of the gRPC channel to the model server, as host:port or as a gRPC target URI such"
                            + " as dns:///host:8500. The default is the gRPC port of TensorFlow Serving on the local host."
                            + " The channel is created once, when the endpoint is initialized.")
    private String target = "localhost:8500";

    @UriParam(label = "security",
              description = "The io.grpc.ChannelCredentials of the gRPC channel, for example a TlsChannelCredentials for"
                            + " TLS, given as a bean reference. If not set, InsecureChannelCredentials is used, and the calls"
                            + " are sent in plaintext.")
    private ChannelCredentials credentials;

    @UriParam(label = "common",
              description = "The name of the model (servable) to call. The CamelTensorFlowServingModelName header replaces"
                            + " this option, and the model_spec of a request in the body replaces both. The option or the"
                            + " header must be set, even when the request in the body names the model.")
    private String modelName;

    @UriParam(label = "common",
              description = "The version number of the model to use. If no version and no version label is set, the server"
                            + " uses the best available version, typically the latest. The CamelTensorFlowServingModelVersion"
                            + " header replaces this option, and a version label, if one is set, is used instead of it.")
    private Long modelVersion;

    @UriParam(label = "common",
              description = "The label of the model version to use, as assigned by version_labels in the model server"
                            + " configuration, for example stable or canary. The CamelTensorFlowServingModelVersionLabel"
                            + " header replaces this option. A version label is used instead of a version number.")
    private String modelVersionLabel;

    @UriParam(label = "common",
              description = "The name of the signature (SignatureDef) of the model to evaluate, for classify, regress and"
                            + " predict. If not set, the server uses the default signature, serving_default. The"
                            + " CamelTensorFlowServingSignatureName header replaces this option.")
    private String signatureName;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public ChannelCredentials getCredentials() {
        return credentials;
    }

    public void setCredentials(ChannelCredentials credentials) {
        this.credentials = credentials;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Long getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(Long modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getModelVersionLabel() {
        return modelVersionLabel;
    }

    public void setModelVersionLabel(String modelVersionLabel) {
        this.modelVersionLabel = modelVersionLabel;
    }

    public String getSignatureName() {
        return signatureName;
    }

    public void setSignatureName(String signatureName) {
        this.signatureName = signatureName;
    }

    public TensorFlowServingConfiguration copy() {
        try {
            return (TensorFlowServingConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
