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
package org.apache.camel.component.alibaba.ots;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.CapacityUnit;
import com.alicloud.openservices.tablestore.model.Column;
import com.alicloud.openservices.tablestore.model.ConsumedCapacity;
import com.alicloud.openservices.tablestore.model.DeleteRowResponse;
import com.alicloud.openservices.tablestore.model.GetRowResponse;
import com.alicloud.openservices.tablestore.model.ListTableResponse;
import com.alicloud.openservices.tablestore.model.PrimaryKey;
import com.alicloud.openservices.tablestore.model.PrimaryKeyColumn;
import com.alicloud.openservices.tablestore.model.PutRowResponse;
import com.alicloud.openservices.tablestore.model.Response;
import com.alicloud.openservices.tablestore.model.Row;
import com.alicloud.openservices.tablestore.model.UpdateRowResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.OpenApiClientSupport;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.ots.constants.OTSProperties;
import org.apache.camel.component.alibaba.ots.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class OTSUtils {

    private OTSUtils() {
    }

    public static SyncClient createClient(OTSEndpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getEndpoint())) {
            throw new IllegalArgumentException("Endpoint is required");
        }
        if (ObjectHelper.isEmpty(endpoint.getInstanceName())) {
            throw new IllegalArgumentException("Instance name is required");
        }

        return new SyncClient(
                endpoint.getEndpoint(),
                resolveAccessKey(endpoint.getAccessKey(), endpoint.getServiceKeys()),
                resolveSecretKey(endpoint.getSecretKey(), endpoint.getServiceKeys()),
                endpoint.getInstanceName());
    }

    public static String resolveAccessKey(String accessKey, ServiceKeys serviceKeys) {
        if (ObjectHelper.isNotEmpty(accessKey)) {
            return accessKey;
        }
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getAccessKey())) {
            return serviceKeys.getAccessKey();
        }
        throw new IllegalArgumentException("Authentication parameter 'access key (AK)' not found");
    }

    public static String resolveSecretKey(String secretKey, ServiceKeys serviceKeys) {
        if (ObjectHelper.isNotEmpty(secretKey)) {
            return secretKey;
        }
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getSecretKey())) {
            return serviceKeys.getSecretKey();
        }
        throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
    }

    public static ClientConfigurations createClientConfigurations(OTSEndpoint endpoint, Exchange exchange) {
        ClientConfigurations configuration = new ClientConfigurations();
        configuration.setOperation(
                OpenApiClientSupport.resolveString(exchange, OTSProperties.OPERATION, endpoint.getOperation()));
        return configuration;
    }

    public static List<String> toListTablesBody(ListTableResponse response) {
        if (response.getTableNames() == null) {
            return List.of();
        }
        return new ArrayList<>(response.getTableNames());
    }

    public static Map<String, Object> toPutRowMap(PutRowResponse response) {
        Map<String, Object> map = new HashMap<>();
        addCommonResponseFields(map, response);
        addConsumedCapacity(map, response.getConsumedCapacity());
        addRow(map, response.getRow());
        return map;
    }

    public static Map<String, Object> toGetRowMap(GetRowResponse response) {
        Map<String, Object> map = new HashMap<>();
        addCommonResponseFields(map, response);
        addConsumedCapacity(map, response.getConsumedCapacity());
        addRow(map, response.getRow());
        if (response.hasNextToken()) {
            map.put("nextToken", Base64.getEncoder().encodeToString(response.getNextToken()));
        }
        return map;
    }

    public static Map<String, Object> toUpdateRowMap(UpdateRowResponse response) {
        Map<String, Object> map = new HashMap<>();
        addCommonResponseFields(map, response);
        addConsumedCapacity(map, response.getConsumedCapacity());
        addRow(map, response.getRow());
        return map;
    }

    public static Map<String, Object> toDeleteRowMap(DeleteRowResponse response) {
        Map<String, Object> map = new HashMap<>();
        addCommonResponseFields(map, response);
        addConsumedCapacity(map, response.getConsumedCapacity());
        addRow(map, response.getRow());
        return map;
    }

    private static void addCommonResponseFields(Map<String, Object> map, Response response) {
        map.put("requestId", response.getRequestId());
        map.put("traceId", response.getTraceId());
    }

    private static void addConsumedCapacity(Map<String, Object> map, ConsumedCapacity consumedCapacity) {
        if (consumedCapacity == null) {
            return;
        }
        Map<String, Object> capacityMap = new HashMap<>();
        CapacityUnit capacityUnit = consumedCapacity.getCapacityUnit();
        if (capacityUnit != null) {
            capacityMap.put("readCapacityUnit", capacityUnit.getReadCapacityUnit());
            capacityMap.put("writeCapacityUnit", capacityUnit.getWriteCapacityUnit());
        }
        map.put("consumedCapacity", capacityMap);
    }

    private static void addRow(Map<String, Object> map, Row row) {
        if (row == null) {
            return;
        }
        Map<String, Object> rowMap = new HashMap<>();
        rowMap.put("primaryKey", toPrimaryKeyMap(row.getPrimaryKey()));
        rowMap.put("columns", toColumnsList(row.getColumns()));
        map.put("row", rowMap);
    }

    private static Map<String, Object> toPrimaryKeyMap(PrimaryKey primaryKey) {
        Map<String, Object> primaryKeyMap = new HashMap<>();
        if (primaryKey == null || primaryKey.isEmpty()) {
            return primaryKeyMap;
        }
        for (PrimaryKeyColumn column : primaryKey.getPrimaryKeyColumns()) {
            primaryKeyMap.put(column.getName(), column.getValue().toString());
        }
        return primaryKeyMap;
    }

    private static List<Map<String, Object>> toColumnsList(Column[] columns) {
        List<Map<String, Object>> columnsList = new ArrayList<>();
        if (columns == null) {
            return columnsList;
        }
        for (Column column : columns) {
            Map<String, Object> columnMap = new HashMap<>();
            columnMap.put("name", column.getName());
            columnMap.put("value", column.getValue().toString());
            if (column.hasSetTimestamp()) {
                columnMap.put("timestamp", column.getTimestamp());
            }
            columnsList.add(columnMap);
        }
        return columnsList;
    }
}
