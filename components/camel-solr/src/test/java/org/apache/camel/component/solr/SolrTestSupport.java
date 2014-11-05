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
package org.apache.camel.component.solr;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.BeforeClass;

public abstract class SolrTestSupport extends CamelTestSupport {

    private static int port;
    private static int httpsPort;

    @BeforeClass
    public static void initPort() throws Exception {
        port = AvailablePortFinder.getNextAvailable(8999);
        httpsPort = AvailablePortFinder.getNextAvailable(8999);
    }

    protected static int getPort() {
        return port;
    }
    
    protected static int getHttpsPort() {
        return httpsPort;
    }

}
