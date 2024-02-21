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
package org.apache.camel.component.thymeleaf;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;

import static java.util.Map.entry;

public abstract class ThymeleafAbstractBaseTest extends CamelTestSupport {

    protected static final String HTML = "HTML";

    protected static final String HTML_SUFFIX = ".html";

    protected static final long CACHE_TIME_TO_LIVE = 500L;

    protected static final String UTF_8_ENCODING = "UTF-8";

    protected static final int ORDER = 1;

    protected static final String PREFIX_SRC_TEST_RESOURCES = "src/test/resources/";

    protected static final String PREFIX_WEB_INF_TEMPLATES = "WEB-INF/templates/";

    protected static final String ORDER_NUMBER = "orderNumber";

    protected static final String JANE = "Jane";

    protected static final String LAST_NAME = "lastName";

    protected static final String FIRST_NAME = "firstName";

    protected static final String ITEM = "item";

    protected static final String SPAZZ_TESTING_SERVICE = "Spazz Testing Service";

    protected static final String THANK_YOU_FOR_YOUR_ORDER = "Thank you for your order number 7 of Widgets for Dummies.";

    protected static final String YOU_WILL_NOTIFIED = "You will be notified when your order ships.";

    protected static final String DIRECT_START = "direct:start";

    protected static final String DIRECT_START_NO_CACHE = "direct:start_a";

    protected static final String DIRECT_START_WITH_CACHE = "direct:start_b";

    protected static final String DIRECT_START_CACHE_TTL = "direct:start_c";

    protected static final String MOCK_RESULT = "mock:result";

    protected BasicHeaderProcessor basicHeaderProcessor = new BasicHeaderProcessor();

    protected ResourceUriHeaderProcessor resourceUriHeaderProcessor = new ResourceUriHeaderProcessor();

    protected TemplateHeaderProcessor templateHeaderProcessor = new TemplateHeaderProcessor();

    protected UrlTemplateHeaderProcessor urlTemplateHeaderProcessor = new UrlTemplateHeaderProcessor();

    protected VariableMapHeaderProcessor variableMapHeaderProcessor = new VariableMapHeaderProcessor();

    protected static String resourceUri() {

        return "org/apache/camel/component/thymeleaf/letter.html";
    }

    protected static String stringTemplate() {

        return """
                <!--/*-->
                    Licensed to the Apache Software Foundation (ASF) under one or more
                    contributor license agreements.  See the NOTICE file distributed with
                    this work for additional information regarding copyright ownership.
                    The ASF licenses this file to You under the Apache License, Version 2.0
                    (the "License"); you may not use this file except in compliance with
                    the License.  You may obtain a copy of the License at

                         http://www.apache.org/licenses/LICENSE-2.0

                    Unless required by applicable law or agreed to in writing, software
                    distributed under the License is distributed on an "AS IS" BASIS,
                    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                    See the License for the specific language governing permissions and
                    limitations under the License.
                <!--*/-->
                <!--/* This code will be removed at thymeleaf parsing time! */-->
                Dear [(${headers.lastName})], [(${headers.firstName})]

                Thank you for your order number [(${exchange.properties.orderNumber})] of [(${headers.item})].

                Regards Camel Riders Bookstore
                [(${body})]""";
    }

    protected static String urlTemplate() {

        return "http://localhost:9843/dontcare.html";
    }

    protected static String variableMapTemplate() {

        return """
                <!--/*-->
                    Licensed to the Apache Software Foundation (ASF) under one or more
                    contributor license agreements.  See the NOTICE file distributed with
                    this work for additional information regarding copyright ownership.
                    The ASF licenses this file to You under the Apache License, Version 2.0
                    (the "License"); you may not use this file except in compliance with
                    the License.  You may obtain a copy of the License at

                         http://www.apache.org/licenses/LICENSE-2.0

                    Unless required by applicable law or agreed to in writing, software
                    distributed under the License is distributed on an "AS IS" BASIS,
                    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                    See the License for the specific language governing permissions and
                    limitations under the License.
                <!--*/-->
                <!--/* This code will be removed at thymeleaf parsing time! */-->
                Dear [(${lastName})], [(${firstName})]

                Thank you for your order number [(${orderNumber})] of [(${item})].

                Regards Camel Riders Bookstore
                [(${body})]""";
    }

    protected String expected() {

        return "\n\n" +
               "Dear Doe, Jane\n" +
               "\n" +
               "Thank you for your order number 7 of Widgets for Dummies.\n" +
               "\n" +
               "Regards Camel Riders Bookstore\n" +
               "Spazz Testing Service";
    }

    @Override
    protected abstract RouteBuilder createRouteBuilder();

    protected static class BasicHeaderProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {

            exchange.getIn().setHeader(LAST_NAME, "Doe");
            exchange.getIn().setHeader(FIRST_NAME, JANE);
            exchange.getIn().setHeader(ITEM, "Widgets for Dummies");

            exchange.setProperty(ORDER_NUMBER, "7");
        }

    }

    protected static class ResourceUriHeaderProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {

            exchange.getIn().setHeader(ThymeleafConstants.THYMELEAF_RESOURCE_URI, resourceUri());
            exchange.getIn().setHeader(LAST_NAME, "Doe");
            exchange.getIn().setHeader(FIRST_NAME, JANE);
            exchange.getIn().setHeader(ITEM, "Widgets for Dummies");

            exchange.setProperty(ORDER_NUMBER, "7");
        }

    }

    protected static class TemplateHeaderProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {

            exchange.getIn().setHeader(ThymeleafConstants.THYMELEAF_TEMPLATE, stringTemplate());
            exchange.getIn().setHeader(LAST_NAME, "Doe");
            exchange.getIn().setHeader(FIRST_NAME, JANE);
            exchange.getIn().setHeader(ITEM, "Widgets for Dummies");

            exchange.setProperty(ORDER_NUMBER, "7");
        }

    }

    protected static class UrlTemplateHeaderProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {

            exchange.getIn().setHeader(ThymeleafConstants.THYMELEAF_TEMPLATE, urlTemplate());
            exchange.getIn().setHeader(LAST_NAME, "Doe");
            exchange.getIn().setHeader(FIRST_NAME, JANE);
            exchange.getIn().setHeader(ITEM, "Widgets for Dummies");

            exchange.setProperty(ORDER_NUMBER, "7");
        }

    }

    protected static class VariableMapHeaderProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {

            exchange.getIn().setHeader(ThymeleafConstants.THYMELEAF_TEMPLATE, variableMapTemplate());
            Map<String, Object> variables = Map.ofEntries(
                    entry(LAST_NAME, "Doe"),
                    entry(FIRST_NAME, JANE),
                    entry(ITEM, "Widgets for Dummies"),
                    entry(ORDER_NUMBER, "7"));

            exchange.getIn().setHeader(ThymeleafConstants.THYMELEAF_VARIABLE_MAP, new HashMap<>(variables));
        }

    }

}
