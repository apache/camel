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
package org.apache.camel.language.jq;

import net.thisptr.jackson.jq.exception.JsonQueryException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class JqLanguageValidateTest {

    @Test
    public void testValidateLanguage() throws Exception {
        JqLanguage lan = new JqLanguage();
        lan.init();
        Assertions.assertTrue(lan.validateExpression(". + [{\"array\": body()}]"));
        JsonQueryException e = assertThrows(JsonQueryException.class, () -> {
            Assertions.assertFalse(lan.validateExpression(". ^^+ [{\"array\": body()}]"));
        });
        Assertions.assertTrue(e.getMessage().startsWith("Cannot compile query"));
    }

}
