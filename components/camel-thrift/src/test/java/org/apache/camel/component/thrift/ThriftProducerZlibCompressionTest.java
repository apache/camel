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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.component.thrift.impl.CalculatorSyncServerImpl;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TZlibTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThriftProducerZlibCompressionTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThriftProducerZlibCompressionTest.class);

    private TServerSocket serverTransport;
    private TServer server;
    @SuppressWarnings({ "rawtypes" })
    private Calculator.Processor processor;

    @RegisterExtension
    AvailablePortFinder.Port thriftTestPort = AvailablePortFinder.find();
    private static final int THRIFT_TEST_NUM1 = 12;
    private static final int THRIFT_TEST_NUM2 = 13;
    private static final int THRIFT_CLIENT_TIMEOUT = 2000;

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void setupResources() throws Exception {
        processor = new Calculator.Processor(new CalculatorSyncServerImpl());

        serverTransport = new TServerSocket(
                new InetSocketAddress(InetAddress.getByName("localhost"), thriftTestPort.getPort()), THRIFT_CLIENT_TIMEOUT);
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
        args.processor(processor);
        args.protocolFactory(new TBinaryProtocol.Factory());
        args.transportFactory(new TZlibTransport.Factory());
        server = new TThreadPoolServer(args);

        Runnable simple = new Runnable() {
            public void run() {
                LOG.info("Thrift server with zlib compression started on port: {}", thriftTestPort.getPort());
                server.serve();
            }
        };
        new Thread(simple).start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop();
            serverTransport.close();
            LOG.info("Thrift server with zlib compression stoped");
        }
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void testCalculateMethodInvocation() {
        LOG.info("Thrift calculate method sync test start");

        List requestBody = new ArrayList();

        requestBody.add(1);
        requestBody.add(new Work(THRIFT_TEST_NUM1, THRIFT_TEST_NUM2, Operation.MULTIPLY));

        Object responseBody = template.requestBody("direct:thrift-zlib-calculate", requestBody);

        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Integer);
        assertEquals(THRIFT_TEST_NUM1 * THRIFT_TEST_NUM2, responseBody);
    }

    @Test
    public void testVoidMethodInvocation() {
        LOG.info("Thrift method with empty parameters and void output sync test start");

        Object requestBody = null;
        Object responseBody = template.requestBody("direct:thrift-zlib-ping", requestBody);
        assertNull(responseBody);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:thrift-zlib-calculate")
                        .to("thrift://localhost:" + thriftTestPort.getPort()
                            + "/org.apache.camel.component.thrift.generated.Calculator?method=calculate&compressionType=ZLIB&synchronous=true");
                from("direct:thrift-zlib-ping")
                        .to("thrift://localhost:" + thriftTestPort.getPort()
                            + "/org.apache.camel.component.thrift.generated.Calculator?method=ping&compressionType=ZLIB&synchronous=true");
            }
        };
    }
}
