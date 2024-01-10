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
package org.apache.camel.coap;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.FileUtil;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.elements.config.Configuration;
import org.junit.jupiter.api.BeforeEach;

public class CoAPTestSupport extends CamelTestSupport {

    protected static final int PORT = AvailablePortFinder.getNextAvailable();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Configuration.createStandardWithoutFile();
    }

    protected CoapClient createClient(String path) {
        return createClient(path, PORT);
    }

    protected CoapClient createClient(String path, int port) {
        String url = String.format("coap://localhost:%d/%s", port, FileUtil.stripLeadingSeparator(path));
        return new CoapClient(url);
    }
}
