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
package org.apache.camel.component.extension.verifier;

import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError;
import org.junit.Assert;
import org.junit.Test;

public class ComponentVerifierTest extends Assert {

    @Test
    public void testGetErrorDetails() {
        VerificationError error = ResultErrorBuilder.withCodeAndDescription(VerificationError.asCode("test_code"), "test error desc")
            .detail(VerificationError.asAttribute("test_attr_1"), "test_detail_1").detail(VerificationError.HttpAttribute.HTTP_CODE, "test_detail_2").build();

        Assert.assertEquals("test_detail_1", error.getDetail(VerificationError.asAttribute("test_attr_1")));
        Assert.assertEquals("test_detail_1", error.getDetail("test_attr_1"));
        Assert.assertEquals("test_detail_2", error.getDetail(VerificationError.HttpAttribute.HTTP_CODE));
        Assert.assertNull(error.getDetail(VerificationError.HttpAttribute.HTTP_TEXT));

        Assert.assertNull(error.getDetail(VerificationError.asAttribute("test_attr_non_existant")));
    }

    @Test
    public void testNullCode() {
        try {
            VerificationError.asCode(null);
            fail("Code must not be null");
        } catch (IllegalArgumentException exp) {
            Assert.assertTrue(exp.getMessage().contains("null"));
        }
    }

    @Test
    public void testNullAttribute() {
        try {
            VerificationError.asAttribute(null);
            fail("Attribute must not be null");
        } catch (IllegalArgumentException exp) {
            Assert.assertTrue(exp.getMessage().contains("null"));
        }
    }

    @Test
    public void testScopeFromString() {
        Assert.assertEquals(ComponentVerifierExtension.Scope.PARAMETERS, ComponentVerifierExtension.Scope.fromString("PaRaMeTeRS"));

        try {
            ComponentVerifierExtension.Scope.fromString("unknown");
            fail();
        } catch (IllegalArgumentException exp) {
        }
    }
}
