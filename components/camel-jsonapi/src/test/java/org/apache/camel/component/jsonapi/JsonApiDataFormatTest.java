/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jsonapi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.github.jasminb.jsonapi.exceptions.DocumentSerializationException;
import com.github.jasminb.jsonapi.exceptions.UnregisteredTypeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JsonApiDataFormatTest extends CamelTestSupport {

    @Test
    public void testJsonApiMarshal() throws Exception {
        Class<?>[] formats = {MyBook.class, MyAuthor.class};
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        MyBook book = this.generateTestDataAsObject();

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, book, baos);

        String jsonApiOutput = baos.toString();
        assertNotNull(jsonApiOutput);
        assertEquals(this.generateTestDataAsString(), jsonApiOutput);
    }

    @Test(expected = DocumentSerializationException.class)
    public void testJsonApiMarshalNoAnnotationOnType() throws Exception {
        Class<?>[] formats = {MyBook.class, MyAuthor.class};
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, new FooBar(), baos);
    }

    @Test(expected = DocumentSerializationException.class)
    public void testJsonApiMarshalWrongType() throws Exception {
        Class<?>[] formats = {MyBook.class, MyAuthor.class};
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, new MyFooBar("bar"), baos);
    }

    @Test
    public void testJsonApiUnmarshall() throws Exception {
        Class<?>[] formats = {MyBook.class, MyAuthor.class};
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat(MyBook.class, formats);

        String jsonApiInput = this.generateTestDataAsString();

        Exchange exchange = new DefaultExchange(context);
        Object outputObj = jsonApiDataFormat.unmarshal(exchange, new ByteArrayInputStream(jsonApiInput.getBytes()));

        assertNotNull(outputObj);
        MyBook book = (MyBook)outputObj;
        assertEquals("Camel in Action", book.getTitle());
        assertEquals("1", book.getAuthor().getAuthorId());
    }

    @Test(expected = UnregisteredTypeException.class)
    public void testJsonApiUnmarshalWrongType() throws Exception {
        Class<?>[] formats = {MyBook.class, MyAuthor.class};
        JsonApiDataFormat jsonApiDataFormat = new JsonApiDataFormat();
        jsonApiDataFormat.setDataFormatTypes(formats);
        jsonApiDataFormat.setMainFormatType(MyBook.class);

        String jsonApiInput = "{\"data\":{\"type\":\"animal\",\"id\":\"camel\",\"attributes\":{\"humps\":\"2\"}}}";

        Exchange exchange = new DefaultExchange(context);
        jsonApiDataFormat.unmarshal(exchange, new ByteArrayInputStream(jsonApiInput.getBytes()));
    }

    @Test
    public void testCamelJsonApiMarshal() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:resultMarshal");
        mock.expectedMessageCount(1);

        MyAuthor author = new MyAuthor();
        author.setAuthorId("1");
        author.setFirstName("Claus");
        author.setLastName("Ibsen");

        Exchange exchange = this.createExchangeWithBody(author);
        Exchange outExchange = template.send("direct:startMarshal", exchange);
        String outBody = outExchange.getIn().getBody(String.class);
        assertNotNull(outBody);
        assertEquals(this.generateAuthorString(), outBody);

        assertMockEndpointsSatisfied();

    }

    @Test
    public void testCamelJsonApiUnmarshal() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:resultUnmarshal");
        mock.expectedMessageCount(1);

        Exchange exchange = this.createExchangeWithBody(this.generateAuthorString());
        Exchange outExchange = template.send("direct:startUnmarshal", exchange);

        assertMockEndpointsSatisfied();

        MyAuthor author = outExchange.getIn().getBody(MyAuthor.class);
        assertNotNull(author);
        assertEquals("1", author.getAuthorId());
        assertEquals("Claus", author.getFirstName());
        assertEquals("Ibsen", author.getLastName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Class<?>[] formats = {MyAuthor.class};
                JsonApiDataFormat jsonApi = new JsonApiDataFormat(MyAuthor.class, formats);

                from("direct:startMarshal").marshal(jsonApi).to("mock:resultMarshal");

                from("direct:startUnmarshal").unmarshal(jsonApi).to("mock:resultUnmarshal");
            }
        };
    }

    private String generateAuthorString() {
        return "{\"data\":{\"type\":\"author\",\"id\":\"1\",\"attributes\":{\"firstName\":\"Claus\",\"lastName\":\"Ibsen\"}}}";
    }

    private String generateTestDataAsString() {
        return "{\"data\":{\"type\":\"book\",\"id\":\"1617292931\",\"attributes\":{\"title\":\"Camel in Action\"},\"relationships\":{\"author\":{\"data\":{\"type\":\"author\",\"id\":\"1\"}}}}}";
    }

    private MyBook generateTestDataAsObject() {
        MyAuthor author = new MyAuthor();
        author.setAuthorId("1");
        author.setFirstName("Claus");
        author.setLastName("Ibsen");

        MyBook book = new MyBook();
        book.setIsbn("1617292931");
        book.setTitle("Camel in Action");
        book.setAuthor(author);

        return book;
    }

}
