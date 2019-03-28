package org.apache.camel.impl;

import com.github.jasminb.jsonapi.exceptions.DocumentSerializationException;
import com.github.jasminb.jsonapi.exceptions.UnregisteredTypeException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class JsonApiDataFormatTest extends TestSupport {

    private CamelContext context;
    private ProducerTemplate producer;
    JsonApiDataFormat jsonApiDataFormat;

    @Override
    @Before
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.setTracing(true);
        producer = context.createProducerTemplate();
        producer.start();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        producer.stop();
        context.stop();
    }

    @Test
    public void test_jsonApi_marshal() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        jsonApiDataFormat = new JsonApiDataFormat();
        jsonApiDataFormat.setDataFormatTypes(formats);

        MyBook book = this.generateTestDataAsObject();

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, book, baos);

        String jsonApiOutput = baos.toString();
        assertNotNull(jsonApiOutput);
        assertEquals(this.generateTestDataAsString(), jsonApiOutput);
    }

    @Test(expected = DocumentSerializationException.class)
    public void test_jsonApi_marshal_no_annotation_on_type() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        jsonApiDataFormat = new JsonApiDataFormat();
        jsonApiDataFormat.setDataFormatTypes(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, new FooBar(), baos);
    }

    @Test(expected = DocumentSerializationException.class)
    public void test_jsonApi_marshal_wrong_type() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        jsonApiDataFormat = new JsonApiDataFormat();
        jsonApiDataFormat.setDataFormatTypes(formats);

        Exchange exchange = new DefaultExchange(context);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonApiDataFormat.marshal(exchange, new MyFooBar("bar"), baos);
    }

    @Test
    public void test_jsonApi_unmarshal() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        jsonApiDataFormat = new JsonApiDataFormat();
        jsonApiDataFormat.setDataFormatTypes(formats);
        jsonApiDataFormat.setMainFormatType(MyBook.class);

        String jsonApiInput = this.generateTestDataAsString();

        Exchange exchange = new DefaultExchange(context);
        Object outputObj = jsonApiDataFormat.unmarshal(exchange, new ByteArrayInputStream(jsonApiInput.getBytes()));

        assertNotNull(outputObj);
        MyBook book = (MyBook)outputObj;
        assertEquals("Camel in Action", book.getTitle());
        assertEquals("1", book.getAuthor().getAuthorId());
    }

    @Test(expected = UnregisteredTypeException.class)
    public void test_jsonApi_unmarshal_wrong_type() throws Exception {
        Class<?>[] formats = { MyBook.class, MyAuthor.class };
        jsonApiDataFormat = new JsonApiDataFormat();
        jsonApiDataFormat.setDataFormatTypes(formats);
        jsonApiDataFormat.setMainFormatType(MyBook.class);

        String jsonApiInput = "{\"data\":{\"type\":\"animal\",\"id\":\"camel\",\"attributes\":{\"humps\":\"2\"}}}";

        Exchange exchange = new DefaultExchange(context);
        jsonApiDataFormat.unmarshal(exchange, new ByteArrayInputStream(jsonApiInput.getBytes()));
    }

    @Ignore
    public void test_jsonApi_with_route() {
        final String title = "Hello Thai Elephant \u0E08";

        // context.addRoutes(new RouteBuilder() {
        // @Override
        // public void configure() {
        // from("direct:start").marshal()
        // .jsonApi("UTF-8")
        // .process(new SampleProcessor());
        // }
        // });
        // context.start();

        MyBook book = new MyBook();
        book.setTitle(title);

        producer.sendBody("direct:start", book);
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
