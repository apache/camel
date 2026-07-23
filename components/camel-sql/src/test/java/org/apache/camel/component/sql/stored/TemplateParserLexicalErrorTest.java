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
package org.apache.camel.component.sql.stored;

import org.apache.camel.component.sql.stored.template.TemplateParser;
import org.apache.camel.component.sql.stored.template.ast.ParseRuntimeException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that a lexical error (character outside the token alphabet) in a stored procedure template is wrapped in
 * {@link ParseRuntimeException} rather than escaping as a raw {@link Error}.
 */
public class TemplateParserLexicalErrorTest extends CamelTestSupport {

    TemplateParser parser;

    @BeforeEach
    void setupTest() {
        parser = new TemplateParser(context.getClassResolver());
    }

    @Test
    void testSemicolonThrowsParseRuntimeException() {
        assertThrows(ParseRuntimeException.class,
                () -> parser.parseTemplate("MYFUNC(INTEGER ${header.foo});"));
    }

    @Test
    void testBacktickThrowsParseRuntimeException() {
        assertThrows(ParseRuntimeException.class,
                () -> parser.parseTemplate("MYFUNC`(INTEGER ${header.foo})"));
    }
}
