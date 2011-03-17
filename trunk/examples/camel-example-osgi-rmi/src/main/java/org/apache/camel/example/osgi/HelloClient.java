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
package org.apache.camel.example.osgi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Client to invoke the RMI service hosted on another JVM running on localhost.
 *
 * @version 
 */
public final class HelloClient {

    private HelloClient() {
        // use Main
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Getting registry");
        Registry registry = LocateRegistry.getRegistry("localhost", 37541);

        System.out.println("Lookup service");
        HelloService hello = (HelloService) registry.lookup("helloServiceBean");

        System.out.println("Invoking RMI ...");
        String out = hello.hello("Client");

        System.out.println(out);
    }

}
