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
package org.apache.camel.impl.console;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.console.DevConsole;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The {@code CamelSqlQuery} header only changes the executed statement when the endpoint sets
 * {@code allowQueryFromHeader=true}, which is off by default. The console must not report the header as the executed
 * query when the endpoint ignored it - the statement never ran, and the header is sender-controlled.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-24718">CAMEL-24718</a>
 */
public class SqlTraceDevConsoleQueryHeaderTest extends ContextTestSupport {

    private static final String ENDPOINT_QUERY = "select * from projects";
    private static final String HEADER_QUERY = "select * from secrets";

    @Test
    public void testHeaderQueryIsIgnoredWhenTheEndpointDisallowsIt() throws Exception {
        Assertions.assertEquals(ENDPOINT_QUERY, tracedQuery(false));
    }

    @Test
    public void testHeaderQueryIsReportedWhenTheEndpointAllowsIt() throws Exception {
        Assertions.assertEquals(HEADER_QUERY, tracedQuery(true));
    }

    @Test
    public void testHeaderQueryIsIgnoredWhenUseMessageBodyForSqlWins() throws Exception {
        // SqlProducer reads the statement from the body before it looks at the header, so the header
        // is not the statement that ran even though allowQueryFromHeader is enabled
        Assertions.assertEquals(ENDPOINT_QUERY, tracedQuery(true, true));
    }

    private String tracedQuery(boolean allowQueryFromHeader) throws Exception {
        return tracedQuery(allowQueryFromHeader, false);
    }

    private String tracedQuery(boolean allowQueryFromHeader, boolean useMessageBodyForSql) throws Exception {
        context.addComponent("sql", new FakeSqlComponent(allowQueryFromHeader, useMessageBodyForSql));

        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("sql-trace");
        Assertions.assertNotNull(con);
        ServiceHelper.startService(con);

        template.sendBodyAndHeader("sql:" + ENDPOINT_QUERY, "body", "CamelSqlQuery", HEADER_QUERY);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        JsonArray statements = (JsonArray) out.get("statements");
        Assertions.assertNotNull(statements, "the console should have traced the sql: send");
        Assertions.assertEquals(1, statements.size());
        return (String) ((JsonObject) statements.get(0)).get("query");
    }

    /**
     * Stands in for camel-sql, which camel-console must not depend on.
     */
    private static final class FakeSqlComponent extends DefaultComponent {

        private final PropertyConfigurer configurer;

        private FakeSqlComponent(boolean allowQueryFromHeader, boolean useMessageBodyForSql) {
            this.configurer = new SqlOptionsConfigurer(allowQueryFromHeader, useMessageBodyForSql);
        }

        @Override
        public PropertyConfigurer getEndpointPropertyConfigurer() {
            return configurer;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new FakeSqlEndpoint(uri, this);
        }
    }

    private static final class FakeSqlEndpoint extends DefaultEndpoint {

        private FakeSqlEndpoint(String uri, DefaultComponent component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    // the statement is not actually executed; only the traced metadata matters here
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException("Consumer not supported");
        }
    }

    private static final class SqlOptionsConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {

        private final boolean allowQueryFromHeader;
        private final boolean useMessageBodyForSql;

        private SqlOptionsConfigurer(boolean allowQueryFromHeader, boolean useMessageBodyForSql) {
            this.allowQueryFromHeader = allowQueryFromHeader;
            this.useMessageBodyForSql = useMessageBodyForSql;
        }

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return switch (name) {
                case "allowQueryFromHeader", "useMessageBodyForSql" -> boolean.class;
                default -> null;
            };
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            return switch (name) {
                case "allowQueryFromHeader" -> allowQueryFromHeader;
                case "useMessageBodyForSql" -> useMessageBodyForSql;
                default -> null;
            };
        }
    }
}
