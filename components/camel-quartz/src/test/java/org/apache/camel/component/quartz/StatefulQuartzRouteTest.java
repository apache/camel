/**
 *
 */
package org.apache.camel.component.quartz;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @author martin.gilday
 *
 */
public class StatefulQuartzRouteTest extends ContextTestSupport {
	protected MockEndpoint resultEndpoint;

    public void testSendAndReceiveMails() throws Exception {
        resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.message(0).header("triggerName").isEqualTo("myTimerName");
        resultEndpoint.message(0).header("triggerGroup").isEqualTo("myGroup");

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            log.debug("Received: " + in + " with headers: " + in.getHeaders());
        }
    }


	/* (non-Javadoc)
	 * @see org.apache.camel.ContextTestSupport#createRouteBuilder()
	 */
	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
            @Override
			public void configure() {
                // START SNIPPET: example
                from("quartz://myGroup/myTimerName?trigger.repeatInterval=2&trigger.repeatCount=1&stateful=true").to("mock:result");
                // END SNIPPET: example
            }
        };
	}

}
