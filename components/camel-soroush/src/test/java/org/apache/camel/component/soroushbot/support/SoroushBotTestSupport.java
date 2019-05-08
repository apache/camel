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

package org.apache.camel.component.soroushbot.support;

import org.apache.camel.component.soroushbot.service.SoroushService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.logging.log4j.LogManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * A support test class for Soroush Bot tests.
 */
public class SoroushBotTestSupport extends CamelTestSupport {

    private static SoroushMockServer soroushMockServer;

    @BeforeClass
    public static void init() throws Exception {
        if (soroushMockServer == null) {
            soroushMockServer = new SoroushMockServer();
            soroushMockServer.start();
        }
        int port = soroushMockServer.getPort();
        SoroushService.get().setAlternativeUrl("http://localhost:" + port);
        LogManager.getLogger().info("soroushMockServer is up on port " + port);
    }

    @AfterClass
    public static void afterClass() {
        SoroushService.get().setAlternativeUrl(null);
    }
}