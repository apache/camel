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
package org.apache.camel.itest.greeter;

import javax.xml.ws.Endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {

    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    Endpoint endpoint;

    public void start() throws Exception {
        LOG.info("Starting Server");
        Object implementor = new GreeterImpl();
        String address = "http://cxf.apache.org/transports/jms";
        endpoint = Endpoint.publish(address, implementor);
    }

    public void stop() {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    public static void main(String args[]) throws Exception {
        Server server = new Server();
        LOG.info("Server ready...");
        server.start();
        Thread.sleep(5 * 60 * 1000);
        LOG.info("Server exiting");
        server.stop();
        System.exit(0);
    }
}
