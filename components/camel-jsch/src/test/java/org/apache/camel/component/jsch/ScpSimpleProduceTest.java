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
import org.junit.Test;

public class ScpSimpleProduceTest extends ScpServerTestSupport {
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testScpSimpleProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");
        Thread.sleep(1000);

        File file = new File(getScpPath() + "/hello.txt");
        assertFileExists(file.getAbsolutePath());
        assertEquals("Hello World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testScpSimpleSubPathProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Bye World", Exchange.FILE_NAME, "mysub/bye.txt");
        Thread.sleep(1000);

        File file = new File(getScpPath() + "/mysub/bye.txt");
        assertFileExists(file.getAbsolutePath());
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testScpSimpleTwoSubPathProduce() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Farewell World", Exchange.FILE_NAME, "mysub/mysubsub/farewell.txt");
        Thread.sleep(1000);

        File file = new File(getScpPath() + "/mysub/mysubsub/farewell.txt");
        assertFileExists(file.getAbsolutePath());
        assertEquals("Farewell World", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testScpProduceChmod() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = getScpUri() + "?username=admin&password=admin&chmod=640&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Bonjour Monde", Exchange.FILE_NAME, "monde.txt");
        Thread.sleep(1000);

        File file = new File(getScpPath() + "/monde.txt");
        assertFileExists(file.getAbsolutePath());
        // Mina sshd we use for testing ignores file perms;
        // assertFalse("File should not have execute rights: " + file, file.canExecute());
        assertEquals("Bonjour Monde", context.getTypeConverter().convertTo(String.class, file));
    }

    @Test
    public void testScpProducePrivateKey() throws Exception {
        Assume.assumeTrue(this.isSetupComplete());

        String uri = getScpUri() + "?username=admin&privateKeyFile=src/test/resources/camel-key.priv&privateKeyFilePassphrase=password&knownHostsFile=" + getKnownHostsFile();
        template.sendBodyAndHeader(uri, "Hallo Welt", Exchange.FILE_NAME, "welt.txt");
        Thread.sleep(1000);

        File file = new File(getScpPath() + "/welt.txt");
        assertFileExists(file.getAbsolutePath());
        // Mina sshd we use for testing ignores file perms;
        // assertFalse("File should not have execute rights: " + file, file.canExecute());
        assertEquals("Hallo Welt", context.getTypeConverter().convertTo(String.class, file));
    }
}
