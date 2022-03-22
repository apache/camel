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
package org.apache.camel.test.infra.fhir.services;

import org.junit.jupiter.api.extension.ExtensionContext;

public class FhirLocalSingletonContainerService extends FhirLocalContainerService
        implements ExtensionContext.Store.CloseableResource {

    private static boolean started;

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        if (!started) {
            started = true;
            // Your "before all tests" startup logic goes here
            // The following line registers a callback hook when the root test context is shut down
            extensionContext.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put("fhir", this);
            super.initialize();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // no op
    }

    @Override
    public void close() {
        super.shutdown();
    }
}
