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

    @UriParam(label = "common", defaultValue = "localhost:8500")
    private String target = "localhost:8500";

    @UriParam(label = "security")
    private ChannelCredentials credentials;

    @UriParam(label = "common")
    private String modelName;

    @UriParam(label = "common")
    private Long modelVersion;

    @UriParam(label = "common")
    private String modelVersionLabel;

    @UriParam(label = "common")
    private String signatureName;

    public String getTarget() {
        return target;
    }

    /**
     * The target URI of the client. See:
     * https://grpc.github.io/grpc-java/javadoc/io/grpc/Grpc.html#newChannelBuilder%28java.lang.String,io.grpc.ChannelCredentials%29
     */
    public void setTarget(String target) {
        this.target = target;
    }

    public ChannelCredentials getCredentials() {
        return credentials;
    }

    /**
     * The credentials of the client.
     */
    public void setCredentials(ChannelCredentials credentials) {
        this.credentials = credentials;
    }

    public String getModelName() {
        return modelName;
    }

    /**
     * Required servable name.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Long getModelVersion() {
        return modelVersion;
    }

    /**
     * Optional choice of which version of the model to use. Use this specific version number.
     */
    public void setModelVersion(Long modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getModelVersionLabel() {
        return modelVersionLabel;
    }

    /**
     * Optional choice of which version of the model to use. Use the version associated with the given label.
     */
    public void setModelVersionLabel(String modelVersionLabel) {
        this.modelVersionLabel = modelVersionLabel;
    }

    public String getSignatureName() {
        return signatureName;
    }

    /**
     * A named signature to evaluate. If unspecified, the default signature will be used.
     */
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
