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
        Assertions.assertEquals("'bar' != 'foo'", code);

        code = parser.parsePredicate("${body} == 'foo'");
        Assertions.assertEquals("body == 'foo'", code);

        code = parser.parsePredicate("${body} != 'foo'");
        Assertions.assertEquals("body != 'foo'", code);

        code = parser.parsePredicate("${body} == 123");
        Assertions.assertEquals("body == 123", code); // integer value

        code = parser.parsePredicate("${body} > 9.95");
        Assertions.assertEquals("body > 9.95d", code); // double value

        code = parser.parsePredicate("${body} > 123456789012345");
        Assertions.assertEquals("body > 123456789012345l", code); // long value

        code = parser.parsePredicate("${bodyAs(int)} == 123");
        Assertions.assertEquals("bodyAs(message, int.class) == 123", code);

        code = parser.parsePredicate("${bodyAs(String).length()} == 4");
        Assertions.assertEquals("bodyAs(message, String.class).length() == 4", code);

        code = parser.parsePredicate("${bodyAs(String).substring(3)} == 'DEF'");
        Assertions.assertEquals("bodyAs(message, String.class).substring(3) == 'DEF'", code);

        code = parser.parsePredicate("${bodyAs(int)} > ${headerAs('foo', int)}");
        Assertions.assertEquals("bodyAs(message, int.class) > headerAs(message, 'foo', int.class)", code);

        code = parser.parsePredicate("${camelContext.getName()} == 'myCamel'");
        Assertions.assertEquals("camelContext.getName() == 'myCamel'", code);

        code = parser.parsePredicate("${camelContext.name} == 'myCamel'");
        Assertions.assertEquals("camelContext.getName() == 'myCamel'", code);

        code = parser.parsePredicate("${camelContext.inflightRepository.size()} > 0");
        Assertions.assertEquals("camelContext.getInflightRepository().size() > 0", code);
    }

    @Test
    public void testParseEmbeddedFunctions() throws Exception {
        CSimplePredicateParser parser = new CSimplePredicateParser();

        String code = parser.parsePredicate("${body.substring(1, ${header.max})} == 'foo'");
        Assertions.assertEquals("body.substring(1, header(message, 'max')) == 'foo'", code);
    }

    @Test
    public void testParseSysFunctions() throws Exception {
        CSimplePredicateParser parser = new CSimplePredicateParser();
        String code = parser.parsePredicate("${sys.foo} != 'bar'");
        Assertions.assertEquals("sys('foo') != 'bar'", code);
        code = parser.parsePredicate("${env.foo} != 'bar'");
        Assertions.assertEquals("sysenv('foo') != 'bar'", code);
        code = parser.parsePredicate("${env:FOO} != 'bar'");
        Assertions.assertEquals("sysenv('FOO') != 'bar'", code);
    }

    @Test
    public void testParseExchangeProperty() throws Exception {
        CSimplePredicateParser parser = new CSimplePredicateParser();
        String code = parser.parsePredicate("${exchangeProperty.foo} != 'bar'");
        Assertions.assertEquals("exchangeProperty(exchange, 'foo') != 'bar'", code);
        code = parser.parsePredicate("${exchangeProperty[foo]} != 'bar'");
        Assertions.assertEquals("exchangeProperty(exchange, 'foo') != 'bar'", code);
        code = parser.parsePredicate("${exchangePropertyAs(foo, com.foo.User)} != 'bar'");
        Assertions.assertEquals("exchangePropertyAs(exchange, 'foo', com.foo.User.class) != 'bar'", code);
        code = parser.parsePredicate("${exchangePropertyAs(foo, com.foo.User).name} != 'bar'");
        Assertions.assertEquals("exchangePropertyAs(exchange, 'foo', com.foo.User.class).getName() != 'bar'", code);
        code = parser.parsePredicate("${exchangePropertyAs(foo, com.foo.User).getName()} != 'bar'");
        Assertions.assertEquals("exchangePropertyAs(exchange, 'foo', com.foo.User.class).getName() != 'bar'", code);
    }

}
