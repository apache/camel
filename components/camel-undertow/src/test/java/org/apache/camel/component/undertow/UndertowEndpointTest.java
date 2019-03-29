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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UndertowEndpointTest {

    UndertowEndpoint endpoint;

    final URI withSlash = URI.create("http://0.0.0.0:8080/");

    final URI withoutSlash = URI.create("http://0.0.0.0:8080");

    @Before
    public void createEndpoint() throws URISyntaxException {
        endpoint = new UndertowEndpoint(null, null);
    }

    @Test
    public void emptyPathShouldBeReplacedWithSlash() {
        endpoint.setHttpURI(withoutSlash);
        assertEquals(withSlash, endpoint.getHttpURI());
    }

    @Test
    public void nonEmptyPathShouldBeKeptSame() {
        endpoint.setHttpURI(withSlash);
        assertEquals(withSlash, endpoint.getHttpURI());
    }
}
