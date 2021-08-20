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
package org.apache.camel.component.huaweicloud.dms.models;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huaweicloud.sdk.core.SdkResponse;

/**
 * List instances response object
 */
public class ListInstancesResponse extends SdkResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "instances")
    private List<DmsInstance> instances;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "instance_num")
    private int instanceNum;

    public ListInstancesResponse withInstances(List<DmsInstance> instances) {
        this.instances = instances;
        return this;
    }

    public ListInstancesResponse addInstance(DmsInstance instance) {
        if (this.instances == null) {
            this.instances = new ArrayList<>();
        }
        this.instances.add(instance);
        return this;
    }

    public List<DmsInstance> getInstances() {
        return instances;
    }

    public void setInstances(List<DmsInstance> instances) {
        this.instances = instances;
    }

    public ListInstancesResponse withInstanceNum(int instanceNum) {
        this.instanceNum = instanceNum;
        return this;
    }

    public int getInstanceNum() {
        return instanceNum;
    }

    public void setInstanceNum(int instanceNum) {
        this.instanceNum = instanceNum;
    }

    @Override
    public String toString() {
        return "ListInstancesResponse{" +
               "instances=" + instances +
               ", instanceNum=" + instanceNum +
               '}';
    }
}
