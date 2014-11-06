#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
// This file was generated from ${archetypeGroupId}/${archetypeArtifactId}/${archetypeVersion}
package ${groupId}.internal;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.commons.lang.Validate;

public class ${className}Route extends RouteBuilder {

    SimpleRegistry registry;

    // Configured fields
    @SuppressWarnings("unused")
    private String camelRouteId;
    @SuppressWarnings("unused")
    private Integer maximumRedeliveries;
    @SuppressWarnings("unused")
    private Long redeliveryDelay;
    @SuppressWarnings("unused")
    private Double backOffMultiplier;
    @SuppressWarnings("unused")
    private Long maximumRedeliveryDelay;
    protected boolean summaryLogging = false;

    public ${className}Route(final SimpleRegistry registry) {
        this.registry = registry;
    }

    @Override
	public void configure() throws Exception {
        checkProperties();

        // Add a bean to Camel context registry
        // registry.put("test", "bean");

        errorHandler(defaultErrorHandler()
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .maximumRedeliveries(maximumRedeliveries)
            .redeliveryDelay(redeliveryDelay)
            .backOffMultiplier(backOffMultiplier)
            .maximumRedeliveryDelay(maximumRedeliveryDelay));

        from("{{from}}")
            .startupOrder(2)
            .routeId(camelRouteId)
            .onCompletion()
                .to("direct:processCompletion")
            .end()
            .removeHeaders("*", "breadcrumbId")
            .to("{{to}}");

        from("direct:processCompletion")
            .startupOrder(1)
            .routeId(camelRouteId + ".completion")
            .choice()
                .when(PredicateBuilder.and(simple("${exception} == null"), PredicateBuilder.constant(summaryLogging)))
                    .to("log:" + camelRouteId +".success?groupInterval=60000")
                .when(PredicateBuilder.and(simple("${exception} == null"), PredicateBuilder.constant(!summaryLogging)))
                    .log("{{messageOk}}")
                .when(PredicateBuilder.constant(summaryLogging))
                    .to("log:" + camelRouteId +".failure?groupInterval=60000")
                .otherwise()
                    .log(LoggingLevel.ERROR, "{{messageError}}")
            .endChoice();
	}

    public void checkProperties() {
        Validate.notNull(camelRouteId, "camelRouteId property is not set");
        Validate.notNull(maximumRedeliveries, "maximumRedeliveries property is not set");
        Validate.notNull(redeliveryDelay, "redeliveryDelay property is not set");
        Validate.notNull(backOffMultiplier, "backOffMultiplier property is not set");
        Validate.notNull(maximumRedeliveryDelay, "maximumRedeliveryDelay property is not set");
    }
}
