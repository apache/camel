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
package org.apache.camel.language.csimple;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CSimplePredicateParserTest {

    @Test
    public void testParse() throws Exception {
        CSimplePredicateParser parser = new CSimplePredicateParser();

        String code = parser.parsePredicate("'bar' != 'foo'");
        Assertions.assertEquals("isNotEqualTo(exchange, \"bar\", \"foo\")", code);

        code = parser.parsePredicate("${body} == 'foo'");
        Assertions.assertEquals("isEqualTo(exchange, body, \"foo\")", code);

        code = parser.parsePredicate("${body} != 'foo'");
        Assertions.assertEquals("isNotEqualTo(exchange, body, \"foo\")", code);

        code = parser.parsePredicate("${body} == 123");
        Assertions.assertEquals("isEqualTo(exchange, body, 123)", code);

        code = parser.parsePredicate("${body} > 9.95");
        Assertions.assertEquals("isGreaterThan(exchange, body, 9.95d)", code); // double value

        code = parser.parsePredicate("${body} > 123456789012345");
        Assertions.assertEquals("isGreaterThan(exchange, body, 123456789012345l)", code); // long value

        code = parser.parsePredicate("${bodyAs(int)} == 123");
        Assertions.assertEquals("isEqualTo(exchange, bodyAs(message, int.class), 123)", code);

        code = parser.parsePredicate("${bodyAs(String).length()} == 4");
        Assertions.assertEquals("isEqualTo(exchange, bodyAs(message, String.class).length(), 4)", code);

        code = parser.parsePredicate("${bodyAs(String).substring(3)} == 'DEF'");
        Assertions.assertEquals("isEqualTo(exchange, bodyAs(message, String.class).substring(3), \"DEF\")", code);

        code = parser.parsePredicate("${bodyAs(int)} > ${headerAs('foo', int)}");
        Assertions.assertEquals("isGreaterThan(exchange, bodyAs(message, int.class), headerAs(message, \"foo\", int.class))",
                code);

        code = parser.parsePredicate("${camelContext.getName()} == 'myCamel'");
        Assertions.assertEquals("isEqualTo(exchange, context.getName(), \"myCamel\")", code);

        code = parser.parsePredicate("${camelContext.name} == 'myCamel'");
        Assertions.assertEquals("isEqualTo(exchange, context.getName(), \"myCamel\")", code);

        code = parser.parsePredicate("${camelContext.inflightRepository.size()} > 0");
        Assertions.assertEquals("isGreaterThan(exchange, context.getInflightRepository().size(), 0)", code);
    }

    @Test
    public void testParseEmbeddedFunctions() throws Exception {
        CSimplePredicateParser parser = new CSimplePredicateParser();

        String code = parser.parsePredicate("${body.substring(1, ${header.max})} == 'foo'");
        Assertions.assertEquals("isEqualTo(exchange, body.substring(1, header(message, \"max\")), \"foo\")", code);
    }

    @Test
    public void testParseSysFunctions() throws Exception {
        CSimplePredicateParser parser = new CSimplePredicateParser();
        String code = parser.parsePredicate("${sys.foo} != 'bar'");
        Assertions.assertEquals("isNotEqualTo(exchange, sys(\"foo\"), \"bar\")", code);
        code = parser.parsePredicate("${env.foo} != 'bar'");
        Assertions.assertEquals("isNotEqualTo(exchange, sysenv(\"foo\"), \"bar\")", code);
        code = parser.parsePredicate("${env:FOO} != 'bar'");
        Assertions.assertEquals("isNotEqualTo(exchange, sysenv(\"FOO\"), \"bar\")", code);
    }

    @Test
    public void testParseExchangeProperty() throws Exception {
        CSimplePredicateParser parser = new CSimplePredicateParser();
        String code = parser.parsePredicate("${exchangeProperty.foo} != 'bar'");
        Assertions.assertEquals("isNotEqualTo(exchange, exchangeProperty(exchange, \"foo\"), \"bar\")", code);
        code = parser.parsePredicate("${exchangeProperty[foo]} != 'bar'");
        Assertions.assertEquals("isNotEqualTo(exchange, exchangeProperty(exchange, \"foo\"), \"bar\")", code);
        code = parser.parsePredicate("${exchangePropertyAs(foo, com.foo.User)} != 'bar'");
        Assertions.assertEquals("isNotEqualTo(exchange, exchangePropertyAs(exchange, \"foo\", com.foo.User.class), \"bar\")",
                code);
        code = parser.parsePredicate("${exchangePropertyAs(foo, com.foo.User).name} != 'bar'");
        Assertions.assertEquals(
                "isNotEqualTo(exchange, exchangePropertyAs(exchange, \"foo\", com.foo.User.class).getName(), \"bar\")", code);
        code = parser.parsePredicate("${exchangePropertyAs(foo, com.foo.User).getName()} != 'bar'");
        Assertions.assertEquals(
                "isNotEqualTo(exchange, exchangePropertyAs(exchange, \"foo\", com.foo.User.class).getName(), \"bar\")", code);
    }

}
