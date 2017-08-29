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
package org.apache.camel.examples.grpc;

import java.io.IOException;
import java.util.logging.Logger;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import org.apache.camel.examples.CamelHelloGrpc;
import org.apache.camel.examples.CamelHelloReply;
import org.apache.camel.examples.CamelHelloRequest;

/**
 * Server that manages startup/shutdown of a server.
 */
public class HelloCamelServer {
    private static final Logger LOG = Logger.getLogger(HelloCamelServer.class.getName());

    private Server server;

    private void start() throws IOException {
        /* The port on which the server should run */
        int port = 8080;
        server = ServerBuilder.forPort(port).addService(new HelloCamelImpl()).build().start();
        LOG.info("Server started. I'm listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                HelloCamelServer.this.stop();
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main needed to launch server from command line
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final HelloCamelServer server = new HelloCamelServer();
        server.start();
        server.blockUntilShutdown();
    }

    static class HelloCamelImpl extends CamelHelloGrpc.CamelHelloImplBase {

        @Override
        public void sayHelloToCamel(CamelHelloRequest req, StreamObserver<CamelHelloReply> responseObserver) {
            CamelHelloReply reply = CamelHelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

}
