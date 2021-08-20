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
import org.apache.camel.component.huaweicloud.dms.models.DmsInstance;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesRequest;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesResponse;
import org.apache.camel.component.huaweicloud.dms.models.QueryInstanceRequest;

@SuppressWarnings("unchecked")
public final class DmsMeta {

    public static final HttpRequestDef<ListInstancesRequest, ListInstancesResponse> LIST_INSTANCES = genForlistInstances();

    public static final HttpRequestDef<QueryInstanceRequest, DmsInstance> QUERY_INSTANCE = genForqueryInstance();

    private DmsMeta() {
    }

    private static HttpRequestDef<ListInstancesRequest, ListInstancesResponse> genForlistInstances() {
        // basic
        HttpRequestDef.Builder<ListInstancesRequest, ListInstancesResponse> builder
                = HttpRequestDef.builder(HttpMethod.GET, ListInstancesRequest.class, ListInstancesResponse.class)
                        .withName("ListInstances")
                        .withUri("/v1.0/{project_id}/instances")
                        .withContentType("application/json");

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
                        .withContentType("application/json");

        // requests
        builder.withRequestField("instance_id",
                LocationType.Path,
                FieldExistence.NON_NULL_NON_EMPTY,
                String.class,
                f -> f.withMarshaller(QueryInstanceRequest::getInstanceId, QueryInstanceRequest::setInstanceId));

        return builder.build();
    }
}
