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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class FtpConsumerProcessStrategyTest extends FtpServerTestSupport {

    private MyStrategy myStrategy;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        myStrategy = new MyStrategy();
        jndi.bind("myStrategy", myStrategy);
        return jndi;
    }

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/" + FTP_ROOT_DIR + "?password=admin&processStrategy=#myStrategy";
    }

    @Test
    public void testFtpConsume() throws Exception {
        if (!canTest()) {
            return;
        }

        sendFile(getFtpUrl(), "Hello World", "hello.txt");

        String out = consumer.receiveBody(getFtpUrl(), 5000, String.class);
        assertNotNull(out);
        assertTrue(out.startsWith("Hello World"));
        assertEquals("Begin should have been invoked 1 times", 1, myStrategy.getInvoked());
    }

    private static class MyStrategy implements GenericFileProcessStrategy {

        private volatile int invoked;

        @Override
        public void prepareOnStartup(GenericFileOperations operations, GenericFileEndpoint endpoint) throws Exception {
            //noop
        }

        @Override
        public boolean begin(GenericFileOperations operations, GenericFileEndpoint endpoint, Exchange exchange, GenericFile file) throws Exception {
            return true;
        }

        @Override
        public void abort(GenericFileOperations operations, GenericFileEndpoint endpoint, Exchange exchange, GenericFile file) throws Exception {
            //noop
        }

        @Override
        public void commit(GenericFileOperations operations, GenericFileEndpoint endpoint, Exchange exchange, GenericFile file) throws Exception {
            invoked++;
        }

        @Override
        public void rollback(GenericFileOperations operations, GenericFileEndpoint endpoint, Exchange exchange, GenericFile file) throws Exception {
            //noop
        }

        int getInvoked() {
            return invoked;
        }
    }
}