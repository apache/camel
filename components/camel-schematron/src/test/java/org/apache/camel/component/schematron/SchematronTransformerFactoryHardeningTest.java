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
package org.apache.camel.component.schematron;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that the schematron rules-compilation {@code TransformerFactory} is hardened. Resolving a schematron
 * endpoint compiles its rules, so these tests assert on endpoint resolution: legitimate rules (whose ISO skeleton
 * stylesheets resolve from the classpath via the URIResolver) still compile, while rules referencing an external entity
 * are refused rather than having the entity resolved.
 */
public class SchematronTransformerFactoryHardeningTest {

    @Test
    void legitimateRulesStillCompileWithHardenedFactory() throws Exception {
        try (CamelContext ctx = new DefaultCamelContext()) {
            ctx.start();
            assertDoesNotThrow(
                    () -> ctx.getEndpoint("schematron:sch/schematron-1.sch", SchematronEndpoint.class),
                    "Legitimate schematron rules must still compile after enabling secure processing");
        }
    }

    @Test
    void externalEntityInRulesIsNotResolved() throws Exception {
        try (CamelContext ctx = new DefaultCamelContext()) {
            ctx.start();
            // Without the hardening this rules file compiles (the external entity is expanded into the assert text);
            // with FEATURE_SECURE_PROCESSING + accessExternalDTD="" the parser refuses the entity, so compilation
            // (triggered on endpoint resolution) must fail instead of resolving it.
            assertThrows(Exception.class,
                    () -> ctx.getEndpoint("schematron:sch/schematron-xxe.sch", SchematronEndpoint.class),
                    "Schematron rules referencing an external entity must fail rather than resolve the entity");
        }
    }
}
