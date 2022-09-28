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
package org.apache.camel.language.python;

import org.apache.camel.test.junit5.LanguageTestSupport;
import org.junit.jupiter.api.Test;

public class PythonTest extends LanguageTestSupport {

    @Test
    public void testPythonExpressions() {
        assertExpression("2 + 3", 5);
        exchange.getMessage().setBody(7);
        assertExpression("2 + body", 9);

        exchange.getMessage().setBody(7);
        exchange.getMessage().setHeader("foo", "bar");
        assertExpression("headers['foo']", "bar");
    }

    @Override
    protected String getLanguageName() {
        return "python";
    }
}
