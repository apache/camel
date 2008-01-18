package org.apache.camel.component.stream;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stream.StreamComponent;

public class StreamRouteBuilderTest extends ContextTestSupport {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		context = createCamelContext();
	}

	protected CamelContext createCamelContext() throws Exception {
		CamelContext camelContext = super.createCamelContext();
		camelContext.addComponent("stream", new StreamComponent());
		return camelContext;
	}

	protected RouteBuilder createRouteBuilder() {
		return new RouteBuilder() {
			public void configure() {
				from("direct:start").setHeader("stream", constant(System.out))
                    .to("stream:err", "stream:out", "stream:file?file=/tmp/foo", "stream:header");
				//from("stream:in").to("stream:out",
					//	"stream:err?delay=5000");
			}
		};
	}

	public void testStringContent() {
		template.sendBody("direct:start", "<content/>");
	}

	public void testBinaryContent() {
		template.sendBody("direct:start", new byte[] { 1, 2, 3, 4 });
	}
}
