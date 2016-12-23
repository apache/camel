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
package org.apache.camel.component.sql.stored;

import java.math.BigInteger;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.sql.stored.template.TemplateParser;
import org.apache.camel.component.sql.stored.template.ast.InputParameter;
import org.apache.camel.component.sql.stored.template.ast.OutParameter;
import org.apache.camel.component.sql.stored.template.ast.ParseRuntimeException;
import org.apache.camel.component.sql.stored.template.ast.Template;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class ParserTest extends CamelTestSupport {

    TemplateParser parser = new TemplateParser();

    @Test
    public void shouldParseOk() {
        Template template = parser.parseTemplate("addnumbers(INTEGER ${header.header1},"
                + "VARCHAR ${property.property1},BIGINT ${header.header2},OUT INTEGER header1)");

        Assert.assertEquals("addnumbers", template.getProcedureName());
        Assert.assertEquals(4, template.getParameterList().size());

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader("header1", 1);
        exchange.setProperty("property1", "constant string");
        exchange.getIn().setHeader("header2", BigInteger.valueOf(2));

        InputParameter param1 = (InputParameter) template.getParameterList().get(0);
        Assert.assertEquals("_0", param1.getName());
        Assert.assertEquals(Types.INTEGER, param1.getSqlType());
        Assert.assertEquals(1, param1.getValueExtractor().eval(exchange, null));

        InputParameter param2 = (InputParameter) template.getParameterList().get(1);
        Assert.assertEquals("_1", param2.getName());
        Assert.assertEquals(Types.VARCHAR, param2.getSqlType());
        Assert.assertEquals("constant string", param2.getValueExtractor().eval(exchange, null));

        InputParameter param3 = (InputParameter) template.getParameterList().get(2);
        Assert.assertEquals("_2", param3.getName());
        Assert.assertEquals(Types.BIGINT, param3.getSqlType());
        Assert.assertEquals(BigInteger.valueOf(2L), param3.getValueExtractor().eval(exchange, null));

        OutParameter sptpOutputNode = (OutParameter) template.getParameterList().get(3);
        Assert.assertEquals("_3", sptpOutputNode.getName());
        Assert.assertEquals(Types.INTEGER, sptpOutputNode.getSqlType());
        Assert.assertEquals("header1", sptpOutputNode.getOutValueMapKey());
    }

    @Test(expected = ParseRuntimeException.class)
    public void noOutputParameterShouldFail() {
        parser.parseTemplate("ADDNUMBERS2"
                + "(INTEGER VALUE1 ${header.v1},INTEGER VALUE2 ${header.v2})");

    }

    @Test(expected = ParseRuntimeException.class)
    public void unexistingTypeShouldFail() {
        parser.parseTemplate("ADDNUMBERS2"
                + "(XML VALUE1 ${header.v1},OUT INTEGER VALUE2 ${header.v2})");
    }

    @Test
    public void nestedSimpleExpression() {
        Exchange exchange = createExchangeWithBody(1);
        exchange.getIn().setHeader("foo", 1);
        exchange.getIn().setHeader("bar", 3);
        Template template = parser.parseTemplate("ADDNUMBERS2(INTEGER ${header.foo},INTEGER ${header.bar})");
        assertEquals(1, ((InputParameter) template.getParameterList().get(0)).getValueExtractor().eval(exchange, null));
        assertEquals(3, ((InputParameter) template.getParameterList().get(1)).getValueExtractor().eval(exchange, null));
    }

    @Test
    public void vendorSpeficSqlType() {
        Template template = parser.parseTemplate("ADDNUMBERS2(1342 ${header.foo})");
        assertEquals(1342, ((InputParameter) template.getParameterList().get(0)).getSqlType());
    }

    @Test
    public void vendorSpeficSqlTypeOut() {
        Template template = parser.parseTemplate("ADDNUMBERS2(OUT 1342 h1)");
        assertEquals(1342, ((OutParameter) template.getParameterList().get(0)).getSqlType());
    }


    @Test
    public void nableIssueSyntax() {
        Map<String, String> params = new HashMap<>();
        params.put("P_STR_IN", "a");
        Template template = parser.parseTemplate("IBS.\"Z$IMS_INTERFACE_WS\".TEST_STR(VARCHAR :#P_STR_IN,OUT VARCHAR P_STR_OUT)");
        assertEquals("a", ((InputParameter) template.getParameterList().get(0)).getValueExtractor().eval(null, params));
        assertEquals("IBS.\"Z$IMS_INTERFACE_WS\".TEST_STR", template.getProcedureName());
    }

    @Test(expected = ParseRuntimeException.class)
    public void unmappedTypeShouldFaild() {
        parser.parseTemplate("ADDNUMBERS2"
                + "(OTHER VALUE1 ${header.v1},INTEGER VALUE2 ${header.v2})");
    }

}
