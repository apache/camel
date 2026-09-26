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
package org.apache.camel.component.sql;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25039: creating an endpoint whose query has a named parameter without the placeholder warns, and names both the
 * offending text and the supported form. The query itself is never changed.
 */
public class SqlNamedParameterWarnTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private CollectingAppender appender;

    private static final class CollectingAppender extends AbstractAppender {
        private final List<String> messages = new ArrayList<>();

        CollectingAppender() {
            super("SqlNamedParameterWarnTest", null, null, true, null);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel() == Level.WARN) {
                synchronized (messages) {
                    messages.add(event.getMessage().getFormattedMessage());
                }
            }
        }

        List<String> warningsFor(String marker) {
            synchronized (messages) {
                return messages.stream().filter(m -> m.contains(marker)).toList();
            }
        }
    }

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .build();

        LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        appender = new CollectingAppender();
        appender.start();
        config.addAppender(appender);
        LoggerConfig lc = config.getLoggerConfig(SqlComponent.class.getName());
        lc.addAppender(appender, Level.WARN, null);
        ctx.updateLoggers();
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (appender != null) {
            LoggerContext ctx = (LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
            ctx.getConfiguration().getLoggerConfig(SqlComponent.class.getName()).removeAppender(appender.getName());
            ctx.updateLoggers();
            appender.stop();
        }
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void testAParameterWithoutThePlaceholderWarnsAndNamesTheSupportedForm() {
        context.getComponent("sql", SqlComponent.class).setDataSource(db);

        SqlEndpoint endpoint = context.getEndpoint(
                "sql:insert into warncustomers (id) values (:customer)", SqlEndpoint.class);

        List<String> warnings = appender.warningsFor("warncustomers");
        assertEquals(1, warnings.size(), "expected one warning, got: " + warnings);
        String warning = warnings.get(0);
        assertTrue(warning.contains(":customer which is not a named parameter"), warning);
        assertTrue(warning.contains(":#name"), warning);
        assertTrue(warning.contains(":#${...}"), warning);

        // the query is reported, and it is not rewritten: only the placeholder substitution touches it
        assertTrue(warning.contains("values (:customer)"), warning);
        assertEquals("insert into warncustomers (id) values (:customer)", endpoint.getQuery());
    }

    @Test
    public void testASupportedParameterDoesNotWarn() {
        context.getComponent("sql", SqlComponent.class).setDataSource(db);

        SqlEndpoint endpoint = context.getEndpoint(
                "sql:insert into quietcustomers (id) values (:#customer)", SqlEndpoint.class);

        assertEquals(List.of(), appender.warningsFor("quietcustomers"));
        // the placeholder is substituted, which is what makes it a named parameter to the prepare strategy
        assertEquals("insert into quietcustomers (id) values (:?customer)", endpoint.getQuery());
    }
}
