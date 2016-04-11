package org.apache.camel.component.sip;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class MessageTest
{

    public static void main(String[] args) throws Exception
    {
        CamelContext context = new DefaultCamelContext();

        context.addRoutes(new RouteBuilder()
        {
            @Override
            public void configure() throws Exception
            {
                from("sip:localhost@127.0.1.1:5061").to("sip:localhost@127.0.1.1:5061");
            }
        });

        context.start();
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBody("hello alice");
    }

}
