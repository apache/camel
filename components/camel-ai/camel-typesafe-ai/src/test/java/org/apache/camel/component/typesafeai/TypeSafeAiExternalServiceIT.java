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
package org.apache.camel.component.typesafeai;

import org.apache.camel.test.infra.typesafeai.mock.TypeSafeAiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the same example routes against a configured System One API. */
class TypeSafeAiExternalServiceIT extends TypeSafeAiExamplesSupport {
    @RegisterExtension
    TypeSafeAiService service = new TypeSafeAiService();

    @BeforeAll
    static void requireExternalService() {
        String baseUrl = System.getenv("TYPESAFE_AI_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = System.getenv("LAYA_BASE_URL");
        }
        assumeTrue(baseUrl != null && !baseUrl.isBlank(),
                "Set TYPESAFE_AI_BASE_URL and TYPESAFE_AI_API_KEY to run the external System One tests");
    }

    @Override
    TypeSafeAiService service() {
        return service;
    }
}
