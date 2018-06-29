package org.apache.camel.builder;

/**
 * No-op implementation of a RouteBuilder which can be easily used for initialization
 * via object initialisers.
 *
 * An easy usage would be
 *
 * <pre>
 *   camelContext.add(new Routes {{
 *
 *       from("file:data/inbox?noop=true")
 *         .to("file:data/outbox");
 *
 *   }});
 * </pre>
 */
public class Routes extends RouteBuilder {
    @Override
    public void configure() throws Exception { }
}
