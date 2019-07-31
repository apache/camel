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
package org.apache.camel.example.cxf.provider;

/**
 * An example demonstrating routing of messages to a JAXWS WebServiceProvider
 * endpoint through a Camel route. The message could be either a SOAP envelope 
 * or plain XML over HTTP as defined by the JAX-WS specification.
 */
public final class CamelCxfExample {

    private CamelCxfExample() {
    }

    public static void main(String args[]) throws Exception {
        Server server = new Server();
        try {
            // start the endpoints
            server.start();
            // set the client's service access point
            Client client = new Client("http://localhost:9000/GreeterContext/SOAPMessageService");
            // invoke the services
            String response = client.invoke();
            System.out.println(response);
        } catch (Exception e) {
            System.out.println("Get the exception " + e);
        } finally {
            server.stop();
            System.exit(0);
        }
    }

}
