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
package org.apache.camel.opentelemetry2.mock;

import java.util.Map;

/**
 * Mock HTTP component for testing SpanKind. This component is recognized by HttpSpanDecorator based on its class name.
 */
public class MockHttpComponent extends org.apache.camel.support.DefaultComponent {

    @Override
    protected org.apache.camel.Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        MockHttpEndpoint endpoint = new MockHttpEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
