package org.apache.camel.component.pubnub.example;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;

import static org.apache.camel.component.pubnub.example.PubNubExampleConstants.PUBNUB_PUBLISH_KEY;
import static org.apache.camel.component.pubnub.example.PubNubExampleConstants.PUBNUB_SUBSCRIBE_KEY;

/**
 * Example of the use of GeoLocation Blocks https://www.pubnub.com/blocks-catalog/geolocation/
 */

public class PubNubGeoLocationExample {

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new GeoLocationRoute());
        main.run();
    }

    static class GeoLocationRoute extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("timer:geotimer")
                .process(exchange -> exchange.getIn().setBody(new Foo("bar", "TEXT")))
                .to("pubnub:eon-maps-geolocation-input?operation=fire&publishKey=" + PUBNUB_PUBLISH_KEY + "&subscribeKey=" + PUBNUB_SUBSCRIBE_KEY);

            from("pubnub:eon-map-geolocation-output?subscribeKey=" + PUBNUB_SUBSCRIBE_KEY)
                .log("${body}");
        }
    }

    static class Foo {
        String foo;
        String text;

        public Foo(String foo, String text) {
            super();
            this.foo = foo;
            this.text = text;
        }
    }
}
