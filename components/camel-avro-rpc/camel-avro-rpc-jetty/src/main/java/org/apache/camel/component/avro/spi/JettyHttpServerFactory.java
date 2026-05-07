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
package org.apache.camel.component.avro.spi;

import java.io.IOException;

import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.jetty.HttpServer;
import org.apache.avro.ipc.specific.SpecificResponder;

/**
 * Default implementation of Avro via http, which is based on Jetty http server. For more details see dependency
 * org.apache.avro:avro-ipc-jetty.
 */
@org.apache.camel.spi.annotations.JdkService("avro-rpc-http-server-factory")
public class JettyHttpServerFactory implements AvroRpcHttpServerFactory {
    @Override
    public Server create(SpecificResponder responder, int port) throws IOException {
        return new HttpServer(responder, port);
    }
}
