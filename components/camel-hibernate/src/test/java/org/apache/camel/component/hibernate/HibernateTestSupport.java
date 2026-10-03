/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.hibernate;

import org.apache.camel.builder.RouteBuilder;

public final class HibernateTestSupport {

    private HibernateTestSupport() {
        // Utility class
    }

    // Query Definitions
    public static final String FIND_ELECTRONICS = "SELECT p FROM Product p WHERE p.category.name = 'Electronics'";
    public static final String FIND_ROLLBACK_LAPTOPS = "SELECT p FROM Product p WHERE p.name = 'RollbackTestLaptop'";
    public static final String FIND_DELETE_HANDLER_ITEMS = "SELECT i FROM Item i WHERE i.name LIKE 'DeleteHandler_%'";

    /**
     * Builds all test routes covering Camel JPA Consumer/Producer patterns and Hibernate 7 endpoint variations.
     */
    public static void configureTestRoutes(RouteBuilder builder) {

        // =========================================================================
        // CONSUMER ROUTES (Polling & JPA Consumer Patterns)
        // =========================================================================

        builder.from("hibernate:org.apache.camel.component.hibernate.Product"
                     + "?query=" + FIND_ELECTRONICS
                     + "&consumeDelete=true"
                     + "&initialDelay=10"
                     + "&delay=100")
                .routeId("consumer-delete")
                .to("mock:consumerDelete");

        builder.from("hibernate:org.apache.camel.component.hibernate.Product"
                     + "?query=" + FIND_ROLLBACK_LAPTOPS
                     + "&consumeDelete=true"
                     + "&initialDelay=10"
                     + "&delay=100")
                .routeId("consumer-rollback")
                .to("mock:rollbackResult");

        builder.from("hibernate:org.apache.camel.component.hibernate.Item"
                     + "?namedQuery=Item.findByName"
                     + "&parameters.name=TargetItem"
                     + "&consumeDelete=true"
                     + "&initialDelay=10"
                     + "&delay=100")
                .routeId("consumer-named-query")
                .to("mock:namedQueryResult");

        builder.from("hibernate:org.apache.camel.component.hibernate.Item"
                     + "?namedQuery=Item.findNativeItem"
                     + "&consumeDelete=true"
                     + "&initialDelay=10"
                     + "&delay=100")
                .routeId("consumer-native-query")
                .to("mock:nativeQueryResult");

        builder.from("hibernate:org.apache.camel.component.hibernate.Product"
                     + "?query=SELECT p FROM Product p WHERE p.price > :minPrice"
                     + "&parameters.minPrice=500.0"
                     + "&consumeDelete=false"
                     + "&initialDelay=10"
                     + "&delay=100")
                .routeId("consumer-query-param")
                .to("mock:queryParamResult");

        // Custom DeleteHandler Route
        builder.from("hibernate:org.apache.camel.component.hibernate.Item"
                     + "?query=" + FIND_DELETE_HANDLER_ITEMS
                     + "&consumeDelete=true"
                     + "&initialDelay=10"
                     + "&delay=100")
                .routeId("consumer-delete-handler")
                .to("mock:deleteHandlerResult");

        // =========================================================================
        // PRODUCER ROUTES (JPA Standard Producer Operations)
        // =========================================================================

        builder.from("direct:jpaPersist")
                .routeId("producer-jpa-persist")
                .to("hibernate:org.apache.camel.component.hibernate.Item");

        // Standard merge/update producer route (default JPA producer behavior)
        builder.from("direct:jpaUpdate")
                .routeId("producer-jpa-update")
                .to("hibernate:org.apache.camel.component.hibernate.Item");

        builder.from("direct:jpaNamedQuery")
                .routeId("producer-jpa-named-query")
                .to("hibernate:org.apache.camel.component.hibernate.Item?namedQuery=Item.findByName");

        builder.from("direct:jpaStrictPersist")
                .routeId("producer-jpa-strict-persist")
                .to("hibernate:org.apache.camel.component.hibernate.Item?usePersist=true");
    }
}
