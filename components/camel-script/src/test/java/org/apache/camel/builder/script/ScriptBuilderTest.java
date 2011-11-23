/**
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
package org.apache.camel.builder.script;

import org.apache.camel.test.junit4.ExchangeTestSupport;
import org.junit.Test;

/**
 *
 */
public class ScriptBuilderTest extends ExchangeTestSupport {

    @Test
    public void testScriptBuilderClasspath() throws Exception {
        ScriptBuilder builder = ScriptBuilder.groovy("classpath:org/apache/camel/builder/script/example/mygroovy.txt");

        exchange.getIn().setBody("foo");
        boolean matches = builder.matches(exchange);
        assertEquals("Should match", true, matches);

        exchange.getIn().setBody("bar");
        matches = builder.matches(exchange);
        assertEquals("Should match", false, matches);
    }

    @Test
    public void testScriptBuilderText() throws Exception {
        ScriptBuilder builder = ScriptBuilder.groovy("request.body == 'foo'");

        exchange.getIn().setBody("foo");
        boolean matches = builder.matches(exchange);
        assertEquals("Should match", true, matches);

        exchange.getIn().setBody("bar");
        matches = builder.matches(exchange);
        assertEquals("Should match", false, matches);
    }

}
