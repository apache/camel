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
package org.apache.camel.component.thrift;

import java.io.IOException;

import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.THsHaServer.Args;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftProducerBaseTest extends CamelTestSupport {
    protected static final int THRIFT_TEST_PORT = AvailablePortFinder.getNextAvailable();
    protected static final int THRIFT_TEST_NUM1 = 12;
    protected static final int THRIFT_TEST_NUM2 = 13;
    @SuppressWarnings({"rawtypes"})
    protected static Calculator.Processor processor;

    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerBaseTest.class);
    private static TNonblockingServerSocket serverTransport;
    private static TServer server;

    @BeforeClass
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void startThriftServer() throws Exception {
        processor = new Calculator.Processor(new CalculatorSyncServerImpl());
        serverTransport = new TNonblockingServerSocket(THRIFT_TEST_PORT);
        server = new THsHaServer(new Args(serverTransport).processor(processor));
        Runnable simple = new Runnable() {
            public void run() {
                LOG.info("Thrift server started on port: {}", THRIFT_TEST_PORT);
                server.serve();
            }
        };
        new Thread(simple).start();
    }

    @AfterClass
    public static void stopThriftServer() throws IOException {
        if (server != null) {
            server.stop();
            serverTransport.close();
            LOG.info("Thrift server stoped");
        }
    }
}
