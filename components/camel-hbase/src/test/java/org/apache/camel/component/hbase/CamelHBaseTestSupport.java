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
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class CamelHBaseTestSupport extends CamelTestSupport {

    //The hbase testing utility has special requirements on the umask.
    //We hold this value to check if the the minicluster has properly started and tests can be run.
    protected static Boolean systemReady = true;

    protected static HBaseTestingUtility hbaseUtil = new HBaseTestingUtility();
    protected static int numServers = 1;
    protected static final String PERSON_TABLE = "person";
    protected static final String INFO_FAMILY = "info";

    protected String[] key = {"1", "2", "3"};
    protected final String[] family = {"info", "birthdate", "address"};
    //comlumn[family][column]
    protected final String[][] column = {
        {"firstName", "middleName", "lastName"},
        {"day", "month", "year"},
        {"street", "number", "zip"}
    };

    //body[row][family][column]
    protected final String[][][] body = {
        {{"Ioannis", "D.", "Canellos"}, {"09", "03", "1980"}, {"Awesome Street", "23", "15344"}},
        {{"John", "", "Dow"}, {"01", "01", "1979"}, {"Unknown Street", "1", "1010"}},
        {{"Jane", "", "Dow"}, {"09", "01", "1979"}, {"Another Unknown Street", "14", "2020"}}
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
            systemReady = false;
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (systemReady) {
            hbaseUtil.shutdownMiniCluster();
        }
    }

    @Override
    public CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(createRegistry());
        HBaseComponent component = new HBaseComponent();
        component.setConfiguration(hbaseUtil.getConfiguration());
        context.addComponent("hbase", component);
        return context;
    }


    protected void putMultipleRows() throws IOException {
        Configuration configuration = hbaseUtil.getHBaseAdmin().getConfiguration();
        HTable table = new HTable(configuration, PERSON_TABLE.getBytes());
        for (int r = 0; r < key.length; r++) {
            Put put = new Put(key[r].getBytes());
            put.add(family[0].getBytes(), column[0][0].getBytes(), body[r][0][0].getBytes());
            table.put(put);
        }

        IOHelper.close(table);
    }
}
