package org.apache.camel.component.sql.sspt;


import org.apache.camel.Exchange;
import org.apache.camel.component.sql.sspt.ast.InputParameter;
import org.apache.camel.component.sql.sspt.ast.OutParameter;
import org.apache.camel.component.sql.sspt.ast.ParseException;
import org.apache.camel.component.sql.sspt.ast.Template;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.sql.Types;

public class ParserTest extends CamelTestSupport {

    SimpleStoredProcedureFactory parser = new SimpleStoredProcedureFactory();


    @Test
    public void shouldParseOk() {


        Template template = parser.parseTemplate("addnumbers(INTEGER ${header.header1}," +
                "VARCHAR ${property.property1},BIGINT ${header.header2},OUT INTEGER header1)");

        Assert.assertEquals("addnumbers", template.getProcedureName());
        Assert.assertEquals(3, template.getInputParameterList().size());

        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader("header1", 1);
        exchange.setProperty("property1", "constant string");
        exchange.getIn().setHeader("header2", BigInteger.valueOf(2));

        InputParameter param1 = template.getInputParameterList().get(0);
        Assert.assertEquals("_0", param1.getName());
        Assert.assertEquals(Types.INTEGER, param1.getSqlType());
        Assert.assertEquals(Integer.valueOf(1), param1.getValueExpression().evaluate(exchange, Integer.class));

        InputParameter param2 = template.getInputParameterList().get(1);
        Assert.assertEquals("_1", param2.getName());
        Assert.assertEquals(Types.VARCHAR, param2.getSqlType());
        Assert.assertEquals("constant string", param2.getValueExpression().evaluate(exchange, String.class));

        InputParameter param3 = template.getInputParameterList().get(2);
        Assert.assertEquals("_2", param3.getName());
        Assert.assertEquals(Types.BIGINT, param3.getSqlType());
        Assert.assertEquals(BigInteger.valueOf(2), param3.getValueExpression().evaluate(exchange, BigInteger.class));

        OutParameter sptpOutputNode = template.getOutParameterList().get(0);
        Assert.assertEquals("_3", sptpOutputNode.getName());
        Assert.assertEquals(Types.INTEGER, sptpOutputNode.getSqlType());
        Assert.assertEquals("header1", sptpOutputNode.getOutHeader());

    }


    @Test(expected = ParseException.class)
    public void noOutputParameterShouldFail() {
        parser.parseTemplate("ADDNUMBERS2" +
                "(INTEGER VALUE1 ${header.v1},INTEGER VALUE2 ${header.v2})");

    }

    @Test(expected = ParseException.class)
    public void unexistingTypeShouldFail() {
        parser.parseTemplate("ADDNUMBERS2" +
                "(XML VALUE1 ${header.v1},OUT INTEGER VALUE2 ${header.v2})");

    }


    @Test(expected = ParseException.class)
    public void unmappedTypeShouldFaild() {
        parser.parseTemplate("ADDNUMBERS2" +
                "(OTHER VALUE1 ${header.v1},INTEGER VALUE2 ${header.v2})");

    }

}
