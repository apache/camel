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
package org.apache.camel.maven.packaging.endpoint;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

@UriEndpoint(scheme = "some", syntax = "some", title = "some", headersClass = SomeConstants.class)
public class SomeEndpoint {

    @UriPath(description = "Hostname of the Foo server")
    @Metadata(required = true)
    private String host;

    public String getHost() {
        return host;
    }

    /**
     * Hostname of the Foo server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public enum MyEnum {
        VAL1,
        VAL2,
        VAL3
    }
}
