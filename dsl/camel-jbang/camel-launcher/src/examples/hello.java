import org.apache.camel.builder.RouteBuilder;

public class hello extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("timer:tick?period=1000")
            .setBody().constant("Hello World!")
            .log("${body}");
    }
}
