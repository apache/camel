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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.kudu.Schema;
import org.apache.kudu.client.CreateTableOptions;
import org.apache.kudu.client.Insert;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduException;
import org.apache.kudu.client.KuduTable;
import org.apache.kudu.client.PartialRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Kudu producer.
 *
 * @see org.apache.camel.component.kudu.KuduEndpoint
 */
public class KuduProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KuduProducer.class);

    private final KuduEndpoint endpoint;

    public KuduProducer(KuduEndpoint endpoint) {
        super(endpoint);

        if (endpoint == null || endpoint.getKuduClient() == null) {
            throw new IllegalArgumentException("Can't create a producer when the database connection is null");
        }
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String table = endpoint.getTableName();
        switch (endpoint.getOperation()) {
            case INSERT:
                doInsert(exchange, table);
                break;
            case CREATE_TABLE:
                doCreateTable(exchange, table);
                break;
            case SCAN:
                doScan(exchange, table);
                break;
            default:
                throw new IllegalArgumentException("The operation " + endpoint.getOperation() + " is not supported");
        }
    }

    private void doInsert(Exchange exchange, String tableName) throws KuduException {
        LOG.trace("Insert on table {}", tableName);
        KuduClient connection = endpoint.getKuduClient();
        KuduTable table = connection.openTable(tableName);


        Insert insert = table.newInsert();
        PartialRow row = insert.getRow();

        Map<?, ?> rows = exchange.getIn().getBody(Map.class);
        for (Map.Entry<?, ?> entry : rows.entrySet()) {
            final String colName = entry.getKey().toString();
            final Object value = entry.getValue();
            //Add only if column exist
            //If not, this will throw an IllegalArgumentException
            if (table.getSchema().getColumn(colName) != null) {
                row.addObject(colName, value);
            }
        }

        connection.newSession().apply(insert);
    }

    private void doCreateTable(Exchange exchange, String tableName) throws KuduException {
        LOG.trace("Creating table {}", tableName);
        KuduClient connection = endpoint.getKuduClient();

        Schema schema = (Schema) exchange.getIn().getHeader(KuduConstants.CAMEL_KUDU_SCHEMA);
        CreateTableOptions builder = (CreateTableOptions)
                exchange.getIn()
                        .getHeader(KuduConstants.CAMEL_KUDU_TABLE_OPTIONS);
        connection.createTable(tableName, schema, builder);

    }

    private void doScan(Exchange exchange, String tableName) throws KuduException {
        exchange.getIn().setBody(KuduUtils.doScan(tableName, endpoint.getKuduClient()));
    }
}
