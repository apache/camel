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
package org.apache.camel.component.twitter.search;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.twitter.AbstractComponentVerifierTest;
import org.apache.camel.component.twitter.AbstractTwitterComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TwitterSearchComponentVerifierTest extends AbstractComponentVerifierTest {
    @Override
    protected String getComponentScheme() {
        return "twitter-search";
    }

    @Test
    public void testEmptyConfiguration() {
        AbstractTwitterComponent component = context().getComponent(getComponentScheme(), AbstractTwitterComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        {
            // Parameters validation
            ComponentVerifierExtension.Result result
                    = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, Collections.emptyMap());

            assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
            assertEquals(5, result.getErrors().size());

            List<String> expected = new LinkedList<>();
            expected.add("keywords");
            expected.add("consumerKey");
            expected.add("consumerSecret");
            expected.add("accessToken");
            expected.add("accessTokenSecret");

            for (ComponentVerifierExtension.VerificationError error : result.getErrors()) {
                expected.removeAll(error.getParameterKeys());
            }

            assertTrue(expected.isEmpty(), "Missing expected params: " + expected.toString());
        }

        {
            // Connectivity validation
            ComponentVerifierExtension.Result result
                    = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, Collections.emptyMap());

            assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
            assertEquals(1, result.getErrors().size());
            assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION,
                    result.getErrors().get(0).getCode());
            assertNotNull(result.getErrors().get(0).getDetails()
                    .get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
            assertTrue(result.getErrors().get(0).getDetails().get(
                    ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof IllegalArgumentException);
        }
    }
}
