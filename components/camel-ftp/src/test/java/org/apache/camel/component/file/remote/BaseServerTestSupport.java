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
package org.apache.camel.component.file.remote;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;

public class BaseServerTestSupport extends CamelTestSupport {

    protected static final String LS = System.lineSeparator();

    protected int port;

    private boolean portInitialized;

    @BeforeEach
    public void initPort() throws Exception {
        if (!portInitialized) {
            // call only once per test method (Some tests can call this method
            // manually in setUp method,
            // which is called before this if setUp method is overridden)
            port = AvailablePortFinder.getNextAvailable();
            portInitialized = true;
        }
    }

    protected int getPort() {
        return port;
    }
}
