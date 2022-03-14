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
package org.apache.camel.component.foo;

import org.apache.camel.Category;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

@UriEndpoint(firstVersion = "1.1.0", scheme = "foo", extendsScheme = "foo", title = "FOO",
    syntax = "foo:host:port", alternativeSyntax = "foo:host@port",
    category = { Category.FILE }, headersClass = FooConstants.class)
public class FooEndpoint {

    @UriPath(description = "Hostname of the Foo server")
    @Metadata(required = true)
    private String host;
    @UriPath(description = "Port of the Foo server")
    private int port;
    @UriParam(label = "common", defaultValue = "5")
    private int intervalSeconds = 5;

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    /**
     * My interval in seconds.
     */
    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname of the Foo server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port of the Foo server
     */
    public void setPort(int port) {
        this.port = port;
    }
}
