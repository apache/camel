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
package org.apache.camel.language.spel;

import org.apache.camel.LanguageTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Exchange variables are exposed on the root object as {@code variables}, {@code getVariable(name)} and
 * {@code getVariable(name, type)}, mirroring {@code headers} and {@code getHeader(...)}.
 */
class SpelVariablesTest extends LanguageTestSupport {

    @Test
    void variablesAreBound() {
        exchange.setVariable("foo", "bar");
        exchange.setVariable("num", 5);

        assertExpression("#{variables.foo}", "bar");
        assertExpression("#{variables['foo']}", "bar");
        assertExpression("#{getVariable('foo')}", "bar");
        assertExpression("#{variables.num + 2}", 7);
        assertExpression("#{getVariable('missing')}", null);
        assertPredicate("#{variables.foo == 'bar'}");
        assertPredicate("#{getVariable('foo') == 'baz'}", false);
    }

    @Test
    void typedVariableGetterConverts() {
        exchange.setVariable("num", "123");

        assertExpression("#{getVariable('num', T(Integer)) + 1}", 124);
        assertPredicate("#{getVariable('num', T(Integer)) > 100}");
    }

    @Test
    void globalVariableThroughGetter() {
        exchange.setVariable("global:greeting", "Hi");

        assertExpression("#{getVariable('global:greeting')}", "Hi");
    }

    @Override
    protected String getLanguageName() {
        return "spel";
    }
}
