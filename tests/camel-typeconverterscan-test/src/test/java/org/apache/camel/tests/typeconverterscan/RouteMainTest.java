package org.apache.camel.tests.typeconverterscan;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class RouteMainTest {

    @Test
    public void testLoadTypeConverter() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.setLoadTypeConverters(true);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").convertBodyTo(MyBean.class);
            }
        });

        context.start();

        Object out = context.createProducerTemplate().requestBody("direct:start", "foo:bar");
        Assert.assertNotNull(out);

        MyBean my = (MyBean) out;
        Assert.assertEquals("foo", my.getA());
        Assert.assertEquals("bar", my.getB());

        context.stop();
    }
}
