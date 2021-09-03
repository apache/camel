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
package org.apache.camel.component.huaweicloud.dms;

import com.huaweicloud.sdk.core.ClientBuilder;
import com.huaweicloud.sdk.core.HcClient;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceResponse;
import org.apache.camel.component.huaweicloud.dms.models.DeleteInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.DeleteInstanceResponse;
import org.apache.camel.component.huaweicloud.dms.models.DmsInstance;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesRequest;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesResponse;
import org.apache.camel.component.huaweicloud.dms.models.QueryInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceResponse;

/**
 * DMS Client class
 */
public class DmsClient {

    protected HcClient hcClient;

    public DmsClient(HcClient hcClient) {
        this.hcClient = hcClient;
    }

    public static ClientBuilder<DmsClient> newBuilder() {
        return new ClientBuilder<>(DmsClient::new);
    }

    public CreateInstanceResponse createInstance(CreateInstanceRequest request) {
        return hcClient.syncInvokeHttp(request, DmsMeta.CREATE_INSTANCE);
    }

    public DeleteInstanceResponse deleteInstance(DeleteInstanceRequest request) {
        return hcClient.syncInvokeHttp(request, DmsMeta.DELETE_INSTANCE);
    }

    public ListInstancesResponse listInstances(ListInstancesRequest request) {
        return hcClient.syncInvokeHttp(request, DmsMeta.LIST_INSTANCES);
    }

    public DmsInstance queryInstance(QueryInstanceRequest request) {
        return hcClient.syncInvokeHttp(request, DmsMeta.QUERY_INSTANCE);
    }

    public UpdateInstanceResponse updateInstance(UpdateInstanceRequest request) {
        return hcClient.syncInvokeHttp(request, DmsMeta.UPDATE_INSTANCE);
    }
}
