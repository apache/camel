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
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestFatJarRouter.class, properties = "spring.main.sources=org.apache.camel.spring.boot.fatjarroutertests")
public class JUnitFatJarRouterTest extends Assert {

    static int port = SocketUtils.findAvailableTcpPort(20000);

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("http.port", port + "");
    }

    @Test
    public void shouldStartCamelRoute() throws InterruptedException, IOException, MalformedObjectNameException {
        String response = IOUtils.toString(new URL("http://localhost:" + port));

        assertEquals("stringBean", response);

        // There should be 3 routes running..
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objectNames = mbs.queryNames(new ObjectName("org.apache.camel:type=routes,*"), null);
        assertEquals(3, objectNames.size());
    }

}

