package my.routes;

import org.apache.camel.builder.RouteBuilder;

public class MyRoutesWithPackage extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("timer:tick")
            .to("log:info");
    }
}