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
package org.apache.camel.component.jsch;

import java.io.File;

import org.apache.camel.Exchange;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class ScpSimpleProduceTest extends ScpServerTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testScpSimpleProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = "scp://localhost:" + getPort() + "/target/scp?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        File file = new File(SCP_ROOT_DIR + "/hello.txt").getAbsoluteFile();
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Ignore("Scenario not supported by scp but could be emulated with recursive copy")
    public void testScpSimpleSubPathProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = "scp://localhost:" + getPort() + "/target/scp?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Bye World", Exchange.FILE_NAME, "bye.txt");

        File file = new File(SCP_ROOT_DIR + "/mysub/bye.txt").getAbsoluteFile();
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    @Ignore("Scenario not supported by scp but could be emulated with recursive copy")
    public void testScpSimpleTwoSubPathProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = "scp://localhost:" + getPort() + "/target/scp?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Farewell World", Exchange.FILE_NAME, "farewell.txt");

        File file = new File(SCP_ROOT_DIR + "/mysub/myother/farewell.txt").getAbsoluteFile();
        assertTrue("File should exist: " + file, file.exists());
        assertEquals("Farewell World", context.getTypeConverter().convertTo(String.class, file));
    }
}
