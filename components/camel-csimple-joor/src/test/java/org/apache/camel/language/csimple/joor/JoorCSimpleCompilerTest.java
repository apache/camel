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
package org.apache.camel.language.csimple.joor;

import org.apache.camel.Exchange;
import org.apache.camel.language.csimple.CSimpleExpression;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JoorCSimpleCompilerTest extends CamelTestSupport {

    @Test
    public void testCompiler() {
        JoorCSimpleCompiler compiler = new JoorCSimpleCompiler();
        compiler.start();

        CSimpleExpression method = compiler.compileExpression(context, "Hello ${body}");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Camel");

        Object out = method.evaluate(exchange, Object.class);
        Assertions.assertEquals("Hello Camel", out);

        compiler.stop();
    }

    @Test
    public void testCompilerPredicate() {
        JoorCSimpleCompiler compiler = new JoorCSimpleCompiler();
        compiler.start();

        CSimpleExpression method = compiler.compilePredicate(context, "${bodyAs(int)} > 100");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("123");
        boolean out = method.matches(exchange);
        Assertions.assertTrue(out);

        exchange.getMessage().setBody("44");
        out = method.matches(exchange);
        Assertions.assertFalse(out);

        compiler.stop();
    }
}
