package org.apache.camel.main.scan;

public class MyConcreteRouteBuilder extends MyAbstractRouteBuilder{

    @Override
    public void configure() throws Exception {
        from("direct:concrete").to("mock:concrete");
    }
}
