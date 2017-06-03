/**
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
package org.apache.camel.cdi.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.rules.Verifier;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class LogVerifier extends Verifier {

    private final Appender appender;

    private final List<String> messages = new ArrayList<>();

    public LogVerifier() {
        appender = newAppender();
    }

    protected void doAppend(org.apache.logging.log4j.core.LogEvent event) {
        messages.add(event.getMessage().getFormattedMessage());
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                    verify();
                } finally {
                }
            }
        };
    }

    private class LogAppender extends AbstractAppender {
        LogAppender(String name) {
            super(
                name,
                null,
                PatternLayout.newBuilder()
                    .withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN)
                    .build()
            );
        }

        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            doAppend(event);
        }
    }

    private Appender newAppender()  {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();

        LogAppender appender = new LogAppender("cdi-rule");
        appender.start();

        config.addAppender(appender);
        config.getRootLogger().removeAppender("cdi-rule");
        config.getRootLogger().addAppender(appender, Level.TRACE, null);

        ctx.updateLoggers();

        return appender;
    }
}