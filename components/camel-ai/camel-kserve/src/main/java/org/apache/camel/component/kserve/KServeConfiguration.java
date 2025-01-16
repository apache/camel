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
package org.apache.camel.component.kserve;

import io.grpc.ChannelCredentials;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
@Configurer
public class KServeConfiguration implements Cloneable {

    @UriParam(label = "common", defaultValue = "localhost:8001")
    private String target = "localhost:8001";

    @UriParam(label = "security")
    private ChannelCredentials credentials;

    @UriParam(label = "common")
    private String modelName;

    @UriParam(label = "common")
    private String modelVersion;

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
     * The name of the model used for inference.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    /**
     * The version of the model used for inference.
     */
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public KServeConfiguration copy() {
        try {
            return (KServeConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
