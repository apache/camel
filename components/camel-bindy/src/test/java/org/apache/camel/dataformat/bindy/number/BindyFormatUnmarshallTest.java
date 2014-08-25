package org.apache.camel.dataformat.bindy.number;

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
import java.util.List;
import java.util.Map;

public class BindyFormatUnmarshallTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BindyFormatUnmarshallTest.class);

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    private String record;

    @Test
    public void testInteger() throws Exception {

        record = "10000,25.12";
        String intVal = "10000";
        String bigDecimal = "25.12";

        template.sendBody(record);

        result.expectedMessageCount(1);
        result.assertIsSatisfied();

        Math math = (Math)result.getExchanges().get(0).getIn().getBody();
        Assert.assertEquals(math.getIntAmount().toString(),intVal);
        Assert.assertEquals(math.getBigDecimal().toString(),bigDecimal);

        LOG.info("Math object received : " + math);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat();
                bindy.setType(BindyType.Csv);
                bindy.setClassType(BindyFormatUnmarshallTest.Math.class);

                from(URI_DIRECT_START)
                   .unmarshal(bindy)
                   .to(URI_MOCK_RESULT);
            }

        };
    }

    @CsvRecord(separator = ",")
    public static class Math {

        @DataField(pos = 1, pattern = "00")
        private Integer intAmount;

        @DataField(pos = 2, precision = 2)
        private BigDecimal bigDecimal;

        public Integer getIntAmount() {
            return intAmount;
        }

        public void setIntAmount(Integer intAmount) {
            this.intAmount = intAmount;
        }

        public BigDecimal getBigDecimal() {
            return bigDecimal;
        }

        public void setBigDecimal(BigDecimal bigDecimal) {
            this.bigDecimal = bigDecimal;
        }

        @Override
        public String toString() {
            return "intAmount : " + this.intAmount + ", " +
                   "bigDecimal : " + this.bigDecimal;
        }
    }
}
