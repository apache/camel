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

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.LogManager;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.rules.Verifier;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class LogVerifier extends Verifier {

    private final Appender appender;

    private final List<String> messages = new ArrayList<>();

    public LogVerifier() {
        this.appender = new AppenderSkeleton() {
            @Override
            public void close() {
            }

            @Override
            public boolean requiresLayout() {
                return false;
            }

            @Override
            protected void append(LoggingEvent event) {
                messages.add(event.getRenderedMessage());
            }
        };
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                LogManager.getRootLogger().addAppender(appender);
                try {
                    base.evaluate();
                    verify();
                } finally {
                    LogManager.getRootLogger().removeAppender(appender);
                    appender.close();
                }
            }
        };
    }
}