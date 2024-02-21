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
package org.apache.camel.component.kudu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.kudu.ColumnSchema;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduException;
import org.apache.kudu.client.KuduPredicate;
import org.apache.kudu.client.KuduScanner;
import org.apache.kudu.client.KuduScannerIterator;
import org.apache.kudu.client.KuduTable;
import org.apache.kudu.client.RowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KuduUtils {

    private static final Logger LOG = LoggerFactory.getLogger(KuduUtils.class);

    private KuduUtils() {
    }

    /**
     * Convert results to a more Java friendly type
     */
    public static List<Map<String, Object>> scannerToList(KuduScanner scanner) {
        KuduScannerIterator it = scanner.iterator();
        List<Map<String, Object>> res = new ArrayList<>();
        while (it.hasNext()) {
            RowResult row = it.next();
            Map<String, Object> r = new HashMap<>();
            res.add(r);
            for (ColumnSchema columnSchema : scanner.getProjectionSchema().getColumns()) {
                final String name = columnSchema.getName();
                r.put(name, row.getObject(name));
            }
        }
        return res;
    }

    public static List<Map<String, Object>> doScan(String tableName, KuduClient connection) throws KuduException {
        return doScan(tableName, connection, null, null, -1);
    }

    public static List<Map<String, Object>> doScan(
            String tableName, KuduClient connection, List<String> columnNames,
            KuduPredicate predicate, long limit)
            throws KuduException {
        LOG.trace("Scanning table {}", tableName);
        KuduTable table = connection.openTable(tableName);

        KuduScanner.KuduScannerBuilder builder = connection.newScannerBuilder(table);
        if (predicate != null) {
            builder.addPredicate(predicate);
        }
        if (-1 < limit) {
            builder.limit(limit);
        }
        List<String> projectColumns = Optional.ofNullable(columnNames).orElse(
                table.getSchema().getColumns().stream().map(ColumnSchema::getName).collect(Collectors.toList()));
        KuduScanner scanner = builder.setProjectedColumnNames(projectColumns).build();
        return KuduUtils.scannerToList(scanner);
    }
}
