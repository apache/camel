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

import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.THsHaServer.Args;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ThriftProducerBaseTest extends CamelTestSupport {
    @RegisterExtension
    AvailablePortFinder.Port thriftTestPort = AvailablePortFinder.find();
    protected static final int THRIFT_TEST_NUM1 = 12;
    protected static final int THRIFT_TEST_NUM2 = 13;
    @SuppressWarnings({ "rawtypes" })
    protected static Calculator.Processor processor;

    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerBaseTest.class);
    private TNonblockingServerSocket serverTransport;
    private TServer server;

    @BeforeEach
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void startThriftServer() throws Exception {
        processor = new Calculator.Processor(new CalculatorSyncServerImpl());
        serverTransport = new TNonblockingServerSocket(thriftTestPort.getPort());
        server = new THsHaServer(new Args(serverTransport).processor(processor));
        Runnable simple = new Runnable() {
            public void run() {
                LOG.info("Thrift server started on port: {}", thriftTestPort.getPort());
                server.serve();
            }
        };
        new Thread(simple).start();
    }

    @AfterEach
    public void stopThriftServer() {
        if (server != null) {
            server.stop();
            serverTransport.close();
            LOG.info("Thrift server stoped");
        }
    }
}
