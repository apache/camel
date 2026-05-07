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

/**
 * Mock HTTP endpoint for testing SpanKind.
 */
class MockHttpEndpoint extends org.apache.camel.support.DefaultEndpoint {

    public MockHttpEndpoint(String endpointUri, org.apache.camel.Component component) {
        super(endpointUri, component);
    }

    @Override
    public org.apache.camel.Producer createProducer() throws Exception {
        return new MockHttpProducer(this);
    }

    @Override
    public org.apache.camel.Consumer createConsumer(org.apache.camel.Processor processor) throws Exception {
        throw new IllegalArgumentException("Not used in MockHttpEndpoint");
    }
}
