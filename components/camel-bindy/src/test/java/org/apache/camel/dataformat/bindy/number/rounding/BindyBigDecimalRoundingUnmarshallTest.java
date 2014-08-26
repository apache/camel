package org.apache.camel.dataformat.bindy.number.rounding;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class BindyBigDecimalRoundingUnmarshallTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BindyBigDecimalRoundingUnmarshallTest.class);

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    private String record;


    @Test
    public void testBigDecimalRoundingUp() throws Exception {

        record = "'12345.789'";
        String bigDecimal = "12345.79";

        template.sendBody(record);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        NumberModel bd = (NumberModel)result.getExchanges().get(0).getIn().getBody();
        Assert.assertEquals(bigDecimal,bd.getRoundingUp().toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat();
                bindy.setType(BindyType.Csv);
                bindy.setClassType(NumberModel.class);
                bindy.setLocale("en");

                from(URI_DIRECT_START)
                   .unmarshal(bindy)
                   .to(URI_MOCK_RESULT);
            }

        };
    }

    @CsvRecord(separator = ",", quote = "'")
    public static class NumberModel {

        @DataField(pos = 1, precision = 2, rounding = "UP", pattern = "#####,##")
        private BigDecimal roundingUp;

        public BigDecimal getRoundingUp() {
            return roundingUp;
        }

        public void setRoundingUp(BigDecimal roundingUp) {
            this.roundingUp = roundingUp;
        }

        @Override
        public String toString() {
            return "BigDecimal rounding : " + this.roundingUp;
        }
    }
}
