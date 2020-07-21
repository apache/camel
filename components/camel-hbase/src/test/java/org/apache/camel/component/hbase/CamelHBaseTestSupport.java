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
package org.apache.camel.component.hbase;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;


public abstract class CamelHBaseTestSupport extends ContainerAwareTestSupport {

    protected static final String PERSON_TABLE = "person";
    protected static final String INFO_FAMILY = "info";

    protected String[] key = {"1", "2", "3"};
    protected final String[] family = {"info", "birthdate", "address"};
    protected final String[][] column = {
        {"id", "firstName", "lastName"},
        {"day", "month", "year"},
        {"street", "number", "zip"}
    };

    //body[row][family][column]
    protected final String[][][] body = {
        {{"1", "Ioannis", "Canellos"}, {"09", "03", "1980"}, {"Awesome Street", "23", "15344"}},
        {{"2", "John", "Dow"}, {"01", "01", "1979"}, {"Unknown Street", "1", "1010"}},
        {{"3", "Christian", "Mueller"}, {"09", "01", "1979"}, {"Another Unknown Street", "14", "2020"}}
    };

    protected final byte[][] families = {
            family[0].getBytes(),
            family[1].getBytes(),
            family[2].getBytes()};

    // init container once for a class
    private GenericContainer cont;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        try {
            createTable(PERSON_TABLE, families);
        } catch (TableExistsException ex) {
            //Ignore if table exists
        }
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        try {
            deleteTable(PERSON_TABLE);
        } catch (TableNotFoundException e) {
            // skip
        }
        super.tearDown();
    }

    @Override
    protected GenericContainer<?> createContainer() {
        if (cont == null) {
            cont = new HBaseContainer();
        }
        return cont;
    }

    @Override
    protected long containersStartupTimeout() {
        // on my laptop it takes around 30-60 seconds to start the cluster.
        return TimeUnit.MINUTES.toSeconds(5);
    }

    protected void putMultipleRows() throws IOException {
        Table table = connectHBase().getTable(TableName.valueOf(PERSON_TABLE.getBytes()));
        for (int r = 0; r < key.length; r++) {
            Put put = new Put(key[r].getBytes());
            put.addColumn(family[0].getBytes(), column[0][0].getBytes(), body[r][0][0].getBytes());
            table.put(put);
        }
        IOHelper.close(table);
    }

    protected Configuration getHBaseConfig() {
        return HBaseContainer.defaultConf();
    }

    protected Connection connectHBase() throws IOException {
        Connection connection = ConnectionFactory.createConnection(getHBaseConfig());
        return connection;
    }

    protected void createTable(String name, byte[][] families) throws IOException {
        TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(TableName.valueOf(name));
        for (byte[] fam : families) {
            builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(fam));
        }
        connectHBase().getAdmin().createTable(builder.build());
    }

    protected void createTable(String name, String family) throws IOException {
        createTable(name, new byte[][]{family.getBytes()});
    }

    protected void deleteTable(String name) throws IOException {
        Admin admin = connectHBase().getAdmin();
        TableName tname = TableName.valueOf(name);
        admin.disableTable(tname);
        admin.deleteTable(tname);
    }
}
