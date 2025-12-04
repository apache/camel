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

package org.apache.camel.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.camel.LanguageTestSupport;
import org.apache.camel.NoSuchLanguageException;
import org.junit.jupiter.api.Test;

public class NoSuchLanguageTest extends LanguageTestSupport {

    @Test
    public void testNoSuchLanguage() {
        NoSuchLanguageException e = assertThrows(
                NoSuchLanguageException.class, () -> assertPredicate("foo"), "Should have thrown an exception");

        assertEquals("No language could be found for: unknown", e.getMessage());
        assertEquals("unknown", e.getLanguage());
    }

    @Override
    protected String getLanguageName() {
        return "unknown";
    }
}
