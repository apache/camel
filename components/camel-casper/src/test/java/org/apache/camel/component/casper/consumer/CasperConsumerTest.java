package org.apache.camel.component.casper.consumer;

import org.apache.camel.component.casper.CasperTestSupport;
import org.apache.camel.component.casper.consumer.sse.SpringAsyncTestApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class CasperConsumerTest extends CasperTestSupport {

	@BeforeAll
	public static void startServer() throws Exception {
		// start sse test server
		SpringAsyncTestApplication.main(new String[0]);
	}

	@AfterAll
	public static void stopServer() throws Exception {
		// shutdown spring boot ctx
		SpringAsyncTestApplication.shutdown();
	}
}
