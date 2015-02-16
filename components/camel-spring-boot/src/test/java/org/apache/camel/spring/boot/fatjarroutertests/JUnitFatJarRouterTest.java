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
package org.apache.camel.spring.boot.fatjarroutertests;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.SocketUtils;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestFatJarRouter.class)
@IntegrationTest("spring.main.sources=org.apache.camel.spring.boot.fatjarroutertests")
public class JUnitFatJarRouterTest extends Assert {

    static int port = SocketUtils.findAvailableTcpPort();

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("http.port", port + "");
    }

    @Test
    public void shouldStartCamelRoute() throws InterruptedException, IOException {
        String response = IOUtils.toString(new URL("http://localhost:" + port));

        assertEquals("stringBean", response);
    }

}

