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
package org.apache.camel.example.camel.transport;

/**
 * Main class to run the Camel transport example.
 */
public final class CamelTransportExample {

    private CamelTransportExample() {
    }

    public static void main(String args[]) throws Exception {
        Server server = new Server();

        try {
            // setup the Camel context for the Camel transport
            server.prepare();
            // start the endpoints
            server.start();
            // set the client's service access point
            Client client = new Client("http://localhost:9001/GreeterContext/GreeterPort");
            // invoking the services
            client.invoke();

            Thread.sleep(1000);

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            server.stop();
            System.exit(0);
        }
    }

}
