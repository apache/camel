package org.apache.camel.component.mongodb3;

import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;

import org.junit.Test;

import org.bson.Document;

public class MongoDbStopEndpointTest extends AbstractMongoDbTest {

	private static final String MY_ID = "myId";

	@EndpointInject(uri = "mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert")
	MongoDbEndpoint endpoint;

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
			public void configure() {
				from("direct:insertJsonString").routeId("insert")
						.to(endpoint);
				from("direct:findById").routeId("find")
						.to("mongodb3:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=findById&dynamicity=true");
			}
		};
	}

	@Test
	public void testStopEndpoint() throws Exception {
		assertEquals(0, testCollection.countDocuments());

		template.requestBody("direct:insertJsonString", "{\"scientist\": \"Newton\", \"_id\": \"" + MY_ID + "\"}");

		endpoint.stop();

		Document result = template.requestBody("direct:findById", MY_ID, Document.class);

		assertEquals(MY_ID, result.get(MONGO_ID));
		assertEquals("Newton", result.get("scientist"));
	}
}
