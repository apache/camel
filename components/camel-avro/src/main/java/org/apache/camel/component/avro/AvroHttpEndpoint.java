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
package org.apache.camel.component.avro;

import org.apache.camel.Component;
import org.apache.camel.Producer;

public class AvroHttpEndpoint extends AvroEndpoint {

    /**
     * Constructs a fully-initialized DefaultEndpoint instance. This is the
     * preferred method of constructing an object from Java code (as opposed to
     * Spring beans, etc.).
     *
     * @param endpointUri the full URI used to create this endpoint
     * @param component   the component that created this endpoint
     */
    public AvroHttpEndpoint(String endpointUri, Component component, AvroConfiguration configuration) {
        super(endpointUri, component, configuration);
    }

    /**
     * Creates a new producer which is used send messages into the endpoint
     *
     * @return a newly created producer
     * @throws Exception can be thrown
     */
    @Override
    public Producer createProducer() throws Exception {
        return new AvroHttpProducer(this);
    }
}
