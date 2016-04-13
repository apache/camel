package org.apache.camel.component.servlet;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.nio.file.Paths;
import java.text.MessageFormat;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * @author arnaud.deprez
 * @since 18/04/16
 */
@RunWith(Arquillian.class)
public class ServletAsyncArquillianTest {

	@Deployment
	public static Archive<?> createTestArchive() {
		// this is a WAR project so use WebArchive
		return ShrinkWrap.create(WebArchive.class)
			// add the web.xml
			.setWebXML(Paths.get("src/test/resources/org/apache/camel/component/servlet/web-spring-async.xml").toFile());
	}

	/**
	 *
	 * @param url the URL is the URL to the web application that was deployed
	 * @throws Exception
	 */
	@Test
	@RunAsClient
	public void testHello(@ArquillianResource URL url) throws Exception {
		final String name = "Arnaud";
		given().
			baseUri(url.toString()).
			queryParam("name", name).
		when().
			get("/services/hello").
		then().
			body(equalTo(MessageFormat.format("Hello {0} how are you?", name)));
	}
}
