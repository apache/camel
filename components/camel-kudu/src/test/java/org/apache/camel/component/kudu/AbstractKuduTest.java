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
import java.util.Arrays;
import java.util.List;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.Schema;
import org.apache.kudu.Type;
import org.apache.kudu.client.CreateTableOptions;
import org.apache.kudu.client.Insert;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduException;
import org.apache.kudu.client.KuduTable;
import org.apache.kudu.client.PartialRow;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractKuduTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractKuduTest.class);

    @Rule
    /**
     * This is the class that connects our Camel test with the
     * Kudu testing framework to spin up a Kudu local endpoint.
     */
    public IntegrationKuduConfiguration ikc = new IntegrationKuduConfiguration();

    private Integer id = 1;

    protected void createTestTable(String tableName) {
        LOG.trace("Creating table " + tableName + ".");
        KuduClient client = ikc.getClient();

        List<ColumnSchema> columns = new ArrayList<>(5);
        final List<String> columnNames = Arrays.asList("id", "title", "name", "lastname", "address");

        for (int i = 0; i < columnNames.size(); i++) {
            Type type = i == 0 ? Type.INT32 : Type.STRING;
            columns.add(
                new ColumnSchema.ColumnSchemaBuilder(columnNames.get(i), type)
                    .key(i == 0)
                    .build()
            );
        }

        List<String> rangeKeys = new ArrayList<>();
        rangeKeys.add("id");

        try {
            client.createTable(tableName,
                new Schema(columns),
                new CreateTableOptions().setRangePartitionColumns(rangeKeys));
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        LOG.trace("Table " + tableName + " created.");
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        ikc.setupCamelContext(this.context);
    }

    @After
    public void tearDown() {
        deleteTestTable("TestTable");
    }

    protected void deleteTestTable(String tableName) {
        LOG.trace("Removing table " + tableName + ".");
        KuduClient client = ikc.getClient();
        try {
            client.deleteTable(tableName);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        LOG.trace("Table " + tableName + " removed.");
    }

    protected void insertRowInTestTable(String tableName) {
        LOG.trace("Inserting row on table " + tableName + ".");
        KuduClient client = ikc.getClient();

        try {
            KuduTable table = client.openTable(tableName);

            Insert insert = table.newInsert();
            PartialRow row = insert.getRow();

            row.addInt("id", id++);
            row.addString("title", "Mr.");
            row.addString("name", "Samuel");
            row.addString("lastname", "Smith");
            row.addString("address", "4359  Plainfield Avenue");

            client.newSession().apply(insert);
        } catch (KuduException e) {

            LOG.error(e.getMessage(), e);
        }
        LOG.trace("Row inserted on table " + tableName + ".");
    }
}
