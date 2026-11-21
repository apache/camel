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
package org.apache.camel.tooling.maven;

/**
 * The proxy of the Maven settings to reach a URL through, with the username and password of the proxy (decrypted with
 * the settings-security.xml), or null ones when it needs no login.
 *
 * @param host     the host of the proxy
 * @param port     the port of the proxy
 * @param username the username, or null
 * @param password the password, or null
 */
public record MavenProxy(String host, int port, String username, String password) {

    /** The proxy as {@code host:port}, without the password. */
    @Override
    public String toString() {
        return (username != null ? username + "@" : "") + host + ":" + port;
    }
}
