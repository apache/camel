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

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class KuduComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpoint() throws Exception {
        String host = "localhost";
        String port = "7051";
        String tableName = "TableName";
        KuduOperations operation = KuduOperations.SCAN;

        KuduComponent component = new KuduComponent(this.context());
        KuduEndpoint endpoint = (KuduEndpoint) component.createEndpoint("kudu:" + host + ":" + port + "/" + tableName + "?operation=" + operation);

        assertEquals("Host was not correctly detected. ", host, endpoint.getHost());
        assertEquals("Port was not correctly detected. ", port, endpoint.getPort());
        assertEquals("Table name was not correctly detected. ", tableName, endpoint.getTableName());
        assertEquals("Operation was not correctly detected. ", operation, endpoint.getOperation());
    }


    @Test(expected = Exception.class)
    public void wrongUrl() throws Exception {

        KuduComponent component = new KuduComponent(this.context());
        component.createEndpoint("wrong url");
    }
}