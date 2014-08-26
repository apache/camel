package org.apache.camel.dataformat.bindy.number.grouping;

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

public class BindyBigDecimalGroupingUnmarshallTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BindyBigDecimalGroupingUnmarshallTest.class);

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    private String record;

    @Test
    public void testBigDecimalPattern() throws Exception {

        record = "'123.456,234'";
        String bigDecimal = "123456.24";

        template.sendBody(record);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        NumberModel bd = (NumberModel)result.getExchanges().get(0).getIn().getBody();
        Assert.assertEquals(bigDecimal, bd.getGrouping().toString());
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

        @DataField(pos = 1, precision = 2,
                rounding = "CEILING",
                pattern = "###,###.###",
                decimalSeparator = ",",
                groupingSeparator = ".")
        private BigDecimal grouping;

        public BigDecimal getGrouping() {
            return grouping;
        }

        public void setGrouping(BigDecimal grouping) {
            this.grouping = grouping;
        }


        @Override
        public String toString() {
            return "bigDecimal grouping : " + this.grouping;
        }
    }
}
