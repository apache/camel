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
package org.apache.camel.impl.verifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError;
import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError.StandardCode;
import org.apache.camel.component.extension.verifier.OptionsGroup;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResultErrorHelperTest {

    OptionsGroup[] groups = new OptionsGroup[] {OptionsGroup.withName("optionA").options("param1", "param2", "!param3"),
                                                OptionsGroup.withName("optionB").options("param1", "!param2", "param3"),
                                                OptionsGroup.withName("optionC").options("!param1", "!param2", "param4")};

    @Test
    public void shouldValidateCorrectParameters() {
        // just giving param1 and param2 is OK
        assertTrue(ResultErrorHelper.requiresAny(map("param1", "param2"), groups).isEmpty());

        // just giving param1 and param3 is OK
        assertTrue(ResultErrorHelper.requiresAny(map("param1", "param3"), groups).isEmpty());

        // just giving param4 is OK
        assertTrue(ResultErrorHelper.requiresAny(map("param4"), groups).isEmpty());
    }

    @Test
    public void shouldValidateParameterExclusions() {
        // combining param2 and param3 is not OK
        final List<ComponentVerifierExtension.VerificationError> results = ResultErrorHelper.requiresAny(map("param1", "param2", "param3"), groups);

        assertEquals(3, results.size());

        final VerificationError param3Error = results.get(0);
        assertEquals(StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION, param3Error.getCode());
        assertEquals("optionA", param3Error.getDetail(VerificationError.GroupAttribute.GROUP_NAME));
        assertEquals("param1,param2,param3", param3Error.getDetail(VerificationError.GroupAttribute.GROUP_OPTIONS));

        final VerificationError param2Error = results.get(1);
        assertEquals(StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION, param2Error.getCode());
        assertEquals("optionB", param2Error.getDetail(VerificationError.GroupAttribute.GROUP_NAME));
        assertEquals("param1,param2,param3", param2Error.getDetail(VerificationError.GroupAttribute.GROUP_OPTIONS));

        final VerificationError param4Error = results.get(2);
        assertEquals(StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION, param4Error.getCode());
        assertEquals("optionC", param4Error.getDetail(VerificationError.GroupAttribute.GROUP_NAME));
        assertEquals("param1,param2,param4", param4Error.getDetail(VerificationError.GroupAttribute.GROUP_OPTIONS));
    }

    static Map<String, Object> map(final String... params) {
        return Arrays.stream(params).collect(Collectors.toMap(e -> e, e -> "value"));
    }
}
