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

import com.huaweicloud.sdk.core.http.FieldExistence;
import com.huaweicloud.sdk.core.http.HttpMethod;
import com.huaweicloud.sdk.core.http.HttpRequestDef;
import com.huaweicloud.sdk.core.http.LocationType;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequestBody;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceResponse;
import org.apache.camel.component.huaweicloud.dms.models.DeleteInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.DeleteInstanceResponse;
import org.apache.camel.component.huaweicloud.dms.models.DmsInstance;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesRequest;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesResponse;
import org.apache.camel.component.huaweicloud.dms.models.QueryInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceRequestBody;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceResponse;

@SuppressWarnings("unchecked")
public final class DmsMeta {

    public static final HttpRequestDef<CreateInstanceRequest, CreateInstanceResponse> CREATE_INSTANCE = genForcreateInstance();

    public static final HttpRequestDef<DeleteInstanceRequest, DeleteInstanceResponse> DELETE_INSTANCE = genFordeleteInstance();

    public static final HttpRequestDef<ListInstancesRequest, ListInstancesResponse> LIST_INSTANCES = genForlistInstances();

    public static final HttpRequestDef<QueryInstanceRequest, DmsInstance> QUERY_INSTANCE = genForqueryInstance();

    public static final HttpRequestDef<UpdateInstanceRequest, UpdateInstanceResponse> UPDATE_INSTANCE = genForupdateInstsance();
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String INSTANCE_ID = "instance_id";

    private DmsMeta() {
    }

    private static HttpRequestDef<CreateInstanceRequest, CreateInstanceResponse> genForcreateInstance() {
        // basic
        HttpRequestDef.Builder<CreateInstanceRequest, CreateInstanceResponse> builder
                = HttpRequestDef.builder(HttpMethod.POST, CreateInstanceRequest.class, CreateInstanceResponse.class)
                        .withName("CreateInstanceKafka")
                        .withUri("/v1.0/{project_id}/instances")
                        .withContentType(JSON_CONTENT_TYPE);

        // requests
        builder.withRequestField("body",
                LocationType.Body,
                FieldExistence.NON_NULL_NON_EMPTY,
                CreateInstanceRequestBody.class,
                f -> f.withMarshaller(CreateInstanceRequest::getBody, CreateInstanceRequest::setBody));

        return builder.build();
    }

    private static HttpRequestDef<DeleteInstanceRequest, DeleteInstanceResponse> genFordeleteInstance() {
        // basic
        HttpRequestDef.Builder<DeleteInstanceRequest, DeleteInstanceResponse> builder
                = HttpRequestDef.builder(HttpMethod.DELETE, DeleteInstanceRequest.class, DeleteInstanceResponse.class)
                        .withName("DeleteInstance")
                        .withUri("/v1.0/{project_id}/instances/{instance_id}")
                        .withContentType(JSON_CONTENT_TYPE);

        // requests
        builder.withRequestField(INSTANCE_ID,
                LocationType.Path,
                FieldExistence.NON_NULL_NON_EMPTY,
                String.class,
                f -> f.withMarshaller(DeleteInstanceRequest::getInstanceId, DeleteInstanceRequest::setInstanceId));

        return builder.build();
    }

    private static HttpRequestDef<ListInstancesRequest, ListInstancesResponse> genForlistInstances() {
        // basic
        HttpRequestDef.Builder<ListInstancesRequest, ListInstancesResponse> builder
                = HttpRequestDef.builder(HttpMethod.GET, ListInstancesRequest.class, ListInstancesResponse.class)
                        .withName("ListInstances")
                        .withUri("/v1.0/{project_id}/instances")
                        .withContentType(JSON_CONTENT_TYPE);

        // requests
        builder.withRequestField("engine",
                LocationType.Query,
                FieldExistence.NULL_IGNORE,
                String.class,
                f -> f.withMarshaller(ListInstancesRequest::getEngine, ListInstancesRequest::setEngine));

        return builder.build();
    }

    private static HttpRequestDef<QueryInstanceRequest, DmsInstance> genForqueryInstance() {
        // basic
        HttpRequestDef.Builder<QueryInstanceRequest, DmsInstance> builder
                = HttpRequestDef.builder(HttpMethod.GET, QueryInstanceRequest.class, DmsInstance.class)
                        .withName("QueryInstance")
                        .withUri("/v1.0/{project_id}/instances/{instance_id}")
                        .withContentType(JSON_CONTENT_TYPE);

        // requests
        builder.withRequestField(INSTANCE_ID,
                LocationType.Path,
                FieldExistence.NON_NULL_NON_EMPTY,
                String.class,
                f -> f.withMarshaller(QueryInstanceRequest::getInstanceId, QueryInstanceRequest::setInstanceId));

        return builder.build();
    }

    private static HttpRequestDef<UpdateInstanceRequest, UpdateInstanceResponse> genForupdateInstsance() {
        // basic
        HttpRequestDef.Builder<UpdateInstanceRequest, UpdateInstanceResponse> builder
                = HttpRequestDef.builder(HttpMethod.PUT, UpdateInstanceRequest.class, UpdateInstanceResponse.class)
                        .withName("UpdateInstance")
                        .withUri("/v1.0/{project_id}/instances/{instance_id}")
                        .withContentType(JSON_CONTENT_TYPE);

        // requests
        builder.withRequestField(INSTANCE_ID,
                LocationType.Path,
                FieldExistence.NON_NULL_NON_EMPTY,
                String.class,
                f -> f.withMarshaller(UpdateInstanceRequest::getInstanceId, UpdateInstanceRequest::setInstanceId));

        builder.withRequestField("body",
                LocationType.Body,
                FieldExistence.NON_NULL_NON_EMPTY,
                UpdateInstanceRequestBody.class,
                f -> f.withMarshaller(UpdateInstanceRequest::getBody, UpdateInstanceRequest::setBody));

        return builder.build();
    }
}
