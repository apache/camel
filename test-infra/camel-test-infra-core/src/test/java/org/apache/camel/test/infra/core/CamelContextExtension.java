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

package org.apache.camel.test.infra.core;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;

/**
 * A JUnit 5 extension that allows you to include a {@link CamelContext} in your test code.
 */
public interface CamelContextExtension extends BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    /**
     * Gets the {@link CamelContext} created by this extension
     *
     * @return an instance of the Camel context to use in the test
     */
    CamelContext getContext();

    /**
     * Creates a {@link ProducerTemplate} from the context
     *
     * @return an instance of a producer template to use with the test
     */
    ProducerTemplate getProducerTemplate();

    /**
     * Creates a {@link ConsumerTemplate} from the context
     *
     * @return an instance of a consumer template to use with the test
     */
    ConsumerTemplate getConsumerTemplate();

    /**
     * Gets a {@link MockEndpoint} for the given URI. If the endpoint does not exist, it will be created
     *
     * @param  uri the URI to create the mock to
     * @return     a Mock endpoint instance for the given URI
     */
    MockEndpoint getMockEndpoint(String uri);

    /**
     * Gets a {@link MockEndpoint} for the given URI.
     *
     * @param  uri                     the URI to create the mock to
     * @param  create                  whether to create the endpoint if it does not exist
     * @return                         a Mock endpoint instance for the given URI
     * @throws NoSuchEndpointException if the endpoint does not exist and a new one should not be created
     */
    MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException;
}
