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
package org.apache.camel.component.hbase;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CamelHBaseTestSupport extends CamelTestSupport {

    //The hbase testing utility has special requirements on the umask.
    //We hold this value to check if the the minicluster has properly started and tests can be run.
    protected static Boolean systemReady = true;

    protected static HBaseTestingUtility hbaseUtil = new HBaseTestingUtility();
    protected static int numServers = 1;
    protected static final String PERSON_TABLE = "person";
    protected static final String INFO_FAMILY = "info";

    private static final Logger LOG = LoggerFactory.getLogger(CamelHBaseTestSupport.class);

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

    @BeforeClass
    public static void setUpClass() throws Exception {
        try {
            hbaseUtil.startMiniCluster(numServers);
        } catch (Exception e) {
            LOG.warn("couldn't start HBase cluster. Test is not started, but passed!", e);
            systemReady = false;
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (systemReady) {
            hbaseUtil.shutdownMiniCluster();
        }
    }

    @Before
    public void setUp() throws Exception {
        if (systemReady) {
            try {
                hbaseUtil.createTable(HBaseHelper.getHBaseFieldAsBytes(PERSON_TABLE), families);
            } catch (TableExistsException ex) {
                //Ignore if table exists
            }

            super.setUp();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (systemReady) {
            hbaseUtil.deleteTable(PERSON_TABLE.getBytes());
            super.tearDown();
        }
    }

    @Override
    public CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(createRegistry());
        // configure hbase component
        HBaseComponent component = context.getComponent("hbase", HBaseComponent.class);
        component.setConfiguration(hbaseUtil.getConfiguration());
        return context;
    }

    protected void putMultipleRows() throws IOException {
        Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
        Connection connection = ConnectionFactory.createConnection(configuration);
        Table table = connection.getTable(TableName.valueOf(PERSON_TABLE.getBytes()));

        for (int r = 0; r < key.length; r++) {
            Put put = new Put(key[r].getBytes());
            put.addColumn(family[0].getBytes(), column[0][0].getBytes(), body[r][0][0].getBytes());
            table.put(put);
        }

        IOHelper.close(table);
    }
}
