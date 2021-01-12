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

public class CSimpleExpressionParserTest {

    @Test
    public void testParse() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("Hello World");
        Assertions.assertEquals("\"Hello World\"", code);
        code = parser.parseExpression("'Hello World'");
        Assertions.assertEquals("\"Hello World\"", code);
        code = parser.parseExpression("Hello ${body}");
        Assertions.assertEquals("\"Hello \" + body", code);
        code = parser.parseExpression("Hello ${body} how are you?");
        Assertions.assertEquals("\"Hello \" + body + \" how are you?\"", code);
    }

    @Test
    public void testIncDec() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${body}++");
        Assertions.assertEquals("increment(exchange, body)", code);
        code = parser.parseExpression("${header.number}--");
        Assertions.assertEquals("decrement(exchange, header(message, \"number\"))", code);
    }

    @Test
    public void testMisc() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${random(10)}");
        Assertions.assertEquals("random(exchange, 0, 10)", code);
        code = parser.parseExpression("${random(10, 20)}");
        Assertions.assertEquals("random(exchange, 10, 20)", code);
        code = parser.parseExpression("${random(10, ${header.max})}");
        Assertions.assertEquals("random(exchange, 10, header(message, \"max\"))", code);
        code = parser.parseExpression("${random(${header.min}, ${header.max})}");
        Assertions.assertEquals("random(exchange, header(message, \"min\"), header(message, \"max\"))", code);

        code = parser.parseExpression("${skip(10)}");
        Assertions.assertEquals("skip(exchange, 10)", code);
        code = parser.parseExpression("${skip(${header.max})}");
        Assertions.assertEquals("skip(exchange, header(message, \"max\"))", code);

        code = parser.parseExpression("${collate(10)}");
        Assertions.assertEquals("collate(exchange, 10)", code);
        code = parser.parseExpression("${collate(${header.max})}");
        Assertions.assertEquals("collate(exchange, header(message, \"max\"))", code);

        code = parser.parseExpression("${messageHistory}");
        Assertions.assertEquals("messageHistory(exchange, true)", code);
        code = parser.parseExpression("${messageHistory(false)}");
        Assertions.assertEquals("messageHistory(exchange, false)", code);
    }

    @Test
    public void testType() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${type:org.apache.camel.Exchange.CONTENT_TYPE}");
        Assertions.assertEquals("type(exchange, org.apache.camel.Exchange.class, \"CONTENT_TYPE\")", code);
        code = parser.parseExpression("${type:org.apache.camel.Exchange.FILE_NAME}");
        Assertions.assertEquals("type(exchange, org.apache.camel.Exchange.class, \"FILE_NAME\")", code);
        code = parser.parseExpression("${type:org.apache.camel.ExchangePattern.InOut}");
        Assertions.assertEquals("type(exchange, org.apache.camel.ExchangePattern.class, \"InOut\")", code);
    }

    @Test
    public void testRef() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${ref:myUser}");
        Assertions.assertEquals("ref(exchange, \"myUser\")", code);
    }

    @Test
    public void testProperties() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${properties:greeting}");
        Assertions.assertEquals("properties(exchange, \"greeting\")", code);
        code = parser.parseExpression("${properties:greeting:hi}");
        Assertions.assertEquals("properties(exchange, \"greeting\", \"hi\")", code);
    }

    @Test
    public void testBean() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${bean:foo}");
        Assertions.assertEquals("bean(exchange, bean, \"foo\", null, null)", code);
        code = parser.parseExpression("${bean:foo?method=bar}");
        Assertions.assertEquals("bean(exchange, bean, \"foo\", \"bar\", null)", code);
        code = parser.parseExpression("${bean:foo?method=bar(123, true)}");
        Assertions.assertEquals("bean(exchange, bean, \"foo\", \"bar(123, true)\", null)", code);
        code = parser.parseExpression("${bean:foo::bar}");
        Assertions.assertEquals("bean(exchange, bean, \"foo\", \"bar\", null)", code);
        code = parser.parseExpression("${bean:foo?method=bar&scope=Prototype}");
        Assertions.assertEquals("bean(exchange, bean, \"foo\", \"bar\", \"Prototype\")", code);
    }

    @Test
    public void testDateWithTimezone() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${date-with-timezone:header.birthday:GMT+8:yyyy-MM-dd'T'HH:mm:ss:SSS}");
        Assertions.assertEquals("date(exchange, \"header.birthday\", \"GMT+8\", \"yyyy-MM-dd'T'HH:mm:ss:SSS\")", code);
    }

    @Test
    public void testDate() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${date:now:hh:mm:ss a}");
        Assertions.assertEquals("date(exchange, \"now\", null, \"hh:mm:ss a\")", code);
        code = parser.parseExpression("${date:now+60s}");
        Assertions.assertEquals("date(exchange, \"now+60s\")", code);
    }

    @Test
    public void testFile() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${file:name}");
        Assertions.assertEquals("fileName(message)", code);
        code = parser.parseExpression("${file:length}");
        Assertions.assertEquals("fileSize(message)", code);
    }

    @Test
    public void testExchange() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${exchange.pattern}");
        Assertions.assertEquals("exchange.getPattern()", code);
    }

    @Test
    public void testExchangeProperty() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("Hello ${exchangeProperty.foo}");
        Assertions.assertEquals("\"Hello \" + exchangeProperty(exchange, \"foo\")", code);

        code = parser.parseExpression("Hello ${exchangePropertyAs(foo, \"com.foo.MyUser\").firstName}");
        Assertions.assertEquals("\"Hello \" + exchangePropertyAs(exchange, \"foo\", com.foo.MyUser.class).getFirstName()",
                code);
    }

    @Test
    public void testException() throws Exception {
        CSimpleExpressionParser parser = new CSimpleExpressionParser();

        String code = parser.parseExpression("${exception}");
        Assertions.assertEquals("exception(exchange)", code);
        code = parser.parseExpression("${exception.cause}");
        Assertions.assertEquals("exception(exchange).getCause()", code);
        code = parser.parseExpression("${exceptionAs(com.foo.MyException).errorCode}");
        Assertions.assertEquals("exceptionAs(exchange, com.foo.MyException.class).getErrorCode()", code);
    }

}
