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
package org.apache.camel.component.docker;

import java.util.HashMap;
import java.util.Map;

import com.github.dockerjava.api.DockerClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DockerConfiguration implements Cloneable {

    @UriPath
    private DockerOperation operation;

    @UriParam
    private DockerClientProfile clientProfile;

    private Map<String, Object> parameters = new HashMap<String, Object>();
    private Map<DockerClientProfile, DockerClient> clients = new HashMap<DockerClientProfile, DockerClient>();

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public DockerOperation getOperation() {
        return operation;
    }

    public void setOperation(DockerOperation operation) {
        this.operation = operation;
    }

    public DockerClient getClient(DockerClientProfile clientProfile) throws DockerException {
        return clients.get(clientProfile);
    }

    public void setClient(DockerClientProfile clientProfile, DockerClient client) {
        clients.put(clientProfile, client);
    }

    public void setClientProfile(DockerClientProfile clientProfile) {
        this.clientProfile = clientProfile;
    }

    public DockerClientProfile getClientProfile() {
        return clientProfile;
    }

    public DockerConfiguration copy() {
        try {
            return (DockerConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }


}
