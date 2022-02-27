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
package org.apache.camel.test.main.junit5;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.MainSupport;

/**
 * An internal implementation of a {@link MainSupport} used to initialize the Camel context the same manner as a real
 * Camel Main application.
 */
final class MainForTest extends MainSupport {

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected CamelContext createCamelContext() {
        throw new UnsupportedOperationException();
    }

    /**
     * Initialize the given Camel context like a Camel Main application.
     *
     * @param  camelContext the Camel context to initialize.
     * @throws Exception    if an error occurs while initializing the Camel context.
     */
    void init(CamelContext camelContext) throws Exception {
        postProcessCamelContext(camelContext);
    }
}
