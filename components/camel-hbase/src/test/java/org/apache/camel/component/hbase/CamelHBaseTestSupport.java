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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class CamelHBaseTestSupport extends CamelTestSupport {

    protected static HBaseTestingUtility hbaseUtil = new HBaseTestingUtility();
    protected static int numServers = 1;
    protected static final String DEFAULTTABLE = "DEFAULTTABLE";
    protected static final String DEFAULTFAMILY = "DEFAULTFAMILY";

    //The hbase testing utility has special requirements on the umask.
    //We hold this value to check if the the minicluster has properly started and tests can be run.
    protected static Boolean systemReady = true;


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
}
