package org.apache.camel.scr;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang.Validate;

public class TestRouteBuilder extends RouteBuilder {

    // Configured fields
    @SuppressWarnings("unused")
    private Integer maximumRedeliveries;
    @SuppressWarnings("unused")
    private Long redeliveryDelay;
    @SuppressWarnings("unused")
    private Double backOffMultiplier;
    @SuppressWarnings("unused")
    private Long maximumRedeliveryDelay;
    @SuppressWarnings("unused")
    private String camelRouteId;

    @Override
    public void configure() throws Exception {
        checkProperties();

        errorHandler(defaultErrorHandler()
                .maximumRedeliveries(maximumRedeliveries)
                .redeliveryDelay(redeliveryDelay)
                .backOffMultiplier(backOffMultiplier)
                .maximumRedeliveryDelay(maximumRedeliveryDelay));

        from("{{from}}")
                .routeId(camelRouteId)
                .log("{{messageOk}}")
                .to("{{to}}");
    }

    public void checkProperties() {
        Validate.notNull(maximumRedeliveries, "maximumRedeliveries property is not set");
        Validate.notNull(redeliveryDelay, "redeliveryDelay property is not set");
        Validate.notNull(backOffMultiplier, "backOffMultiplier property is not set");
        Validate.notNull(maximumRedeliveryDelay, "maximumRedeliveryDelay property is not set");
        Validate.notNull(camelRouteId, "camelRouteId property is not set");
    }
}
