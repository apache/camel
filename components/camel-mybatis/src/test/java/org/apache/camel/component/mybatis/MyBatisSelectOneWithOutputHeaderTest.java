package org.apache.camel.component.mybatis;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MyBatisSelectOneWithOutputHeaderTest extends MyBatisTestSupport {

	private static final String TEST_CASE_HEADER_NAME = "testCaseHeader";
	private static final int TEST_ACCOUNT_ID = 456;
	
    @Test
    public void testSelectOneWithOutputHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(TEST_CASE_HEADER_NAME).isInstanceOf(Account.class);
        mock.message(0).body().isEqualTo(TEST_ACCOUNT_ID);
        mock.message(0).header(MyBatisConstants.MYBATIS_RESULT).isNull();

        template.sendBody("direct:start", TEST_ACCOUNT_ID);

        assertMockEndpointsSatisfied();

        Account account = mock.getReceivedExchanges().get(0).getIn().getHeader(TEST_CASE_HEADER_NAME, Account.class);
        assertEquals("Claus", account.getFirstName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .to("mybatis:selectAccountById?statementType=SelectOne&outputHeader=" + TEST_CASE_HEADER_NAME)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
	
}
