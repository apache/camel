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
package org.apache.camel.commands.jolokia;

import org.apache.camel.commands.CamelController;
import org.jolokia.client.J4pClient;

public interface JolokiaCamelController extends CamelController {

    /**
     * To use the existing {@link org.jolokia.client.J4pClient} with this controller.
     *
     * @param client the client to use
     */
    void using(J4pClient client);

    /**
     * Connects to the remote JVM using the given url to the remote Jolokia agent
     *
     * @param url the url for the remote jolokia agent
     * @param username optional username
     * @param password optional password
     * @throws Exception can be thrown
     */
    void connect(String url, String username, String password) throws Exception;

    /**
     * After connecting the ping command can be used to check if the connection works.
     *
     * @return <tt>true</tt> if connection works, <tt>false</tt> otherwise
     */
    boolean ping();

}
