package org.apache.camel.component.cxf.jaxrs;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.Main;
import org.apache.camel.test.junit4.TestSupport;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.junit.Test;

public class CxfRsRelayTest extends TestSupport {
	/**
	 * A sample service "interface" (technically, it is a class since we will
	 * use proxy-client. That interface exposes three methods over-loading each
	 * other : we are testing the appropriate one will be chosen at runtime.
	 * 
	 */
	@WebService
	@Path("/rootpath")
	@Consumes("multipart/form-data")
	@Produces("application/xml")
	public static class UploadService {
		@WebMethod
		@POST
		@Path("/path1")
		@Consumes("multipart/form-data")
		public void upload(
				@Multipart(value = "content", type = "application/octet-stream") java.lang.Number content,
				@Multipart(value = "name", type = "text/plain") String name) {
		}

		@WebMethod
		@GET
		@Path("/path2")
		@Consumes("text/plain")
		private void upload() {
		}

		@WebMethod
		@POST
		@Path("/path3")
		@Consumes("multipart/form-data")
		public void upload(
				@Multipart(value = "content", type = "application/octet-stream") InputStream content,
				@Multipart(value = "name", type = "text/plain") String name) {
		}

	}

	private static final String SAMPLE_CONTENT_PATH = "/org/apache/camel/component/cxf/jaxrs/CxfRsSpringRelay.xml";
	private static final String SAMPLE_NAME = "CxfRsSpringRelay.xml";
	private static final CountDownLatch latch = new CountDownLatch(1);
	private static String content;
	private static String name;

	/**
	 * That test builds a route chaining two cxfrs endpoints. It shows a request
	 * sent to the first one will be correctly transferred and consumed by the
	 * other one.
	 */
	@Test
	public void test() throws Exception {
		final Main main = new Main();
		try {
			main.setApplicationContextUri("org/apache/camel/component/cxf/jaxrs/CxfRsSpringRelay.xml");
			main.start();
			latch.await(10, TimeUnit.SECONDS);
			assertEquals(SAMPLE_NAME, name);
			StringWriter writer = new StringWriter();
			IOUtils.copyAndCloseInput(
					new InputStreamReader(CamelRouteBuilder.class
							.getResourceAsStream(SAMPLE_CONTENT_PATH)), writer);
			assertEquals(writer.toString(), content);
		} finally {
			main.stop();
		}
	}

	/**
	 * Route builder to be used with
	 * org/apache/camel/component/cxf/jaxrs/CxfRsSpringRelay.xml
	 * 
	 */
	public static class CamelRouteBuilder extends RouteBuilder {
		@Override
		public void configure() throws InterruptedException {
			from("upload1").process(new Processor() {
				@Override
				public void process(Exchange arg0) throws Exception {
					// arg0.getIn().removeHeader(Exchange.CONTENT_TYPE);
				}
			}).to("upload2Client");
			from("upload2").process(new Processor() {
				@Override
				public void process(Exchange exchange) throws Exception {
					// once the message arrives in the second endpoint, stores
					// the message components and warns results can be compared
					content = (exchange.getIn().getHeader("content", String.class));
					name = (exchange.getIn().getHeader("name", String.class));
					latch.countDown();
				}
			});
			Thread t = new Thread(new Runnable() {
				/**
				 * Sends a request to the first endpoint in the route
				 */
				public void run() {
					try {
						JAXRSClientFactory
								.create(getContext().getEndpoint("upload1", CxfRsEndpoint.class).getAddress(),
										UploadService.class)
								.upload(CamelRouteBuilder.class
										.getResourceAsStream(SAMPLE_CONTENT_PATH),
										SAMPLE_NAME);
					} catch (Exception e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}
			});
			t.start();
		}
	}
}
