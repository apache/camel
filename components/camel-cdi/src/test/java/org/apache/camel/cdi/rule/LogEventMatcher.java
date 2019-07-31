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
package org.apache.camel.cdi.rule;

import java.util.Objects;

import org.hamcrest.Description;
import org.hamcrest.DiagnosingMatcher;
import org.hamcrest.Matcher;

public final class LogEventMatcher extends DiagnosingMatcher<LogEvent> {

    private String level;

    private String logger;

    private Matcher<String> message;

    private LogEventMatcher() {
    }

    public static LogEventMatcher logEvent() {
        return new LogEventMatcher();
    }

    @Override
    public boolean matches(Object item, Description description) {
        LogEvent target = (LogEvent) item;

        if (!match(target)) {
            description.appendText(" was LogEvent with ")
                .appendText("level [" + Objects.toString(target.getLevel(), "n/a") + "], ")
                .appendText("logger [" + Objects.toString(target.getLogger(), "n/a") + "], ")
                .appendText("message [" + Objects.toString(target.getMessage(), "n/a") + "]");
            return false;
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("LogEvent with ")
            .appendText("level [" + Objects.toString(level, "n/a") + "], ")
            .appendText("logger [" + Objects.toString(logger, "n/a") + "], ")
            .appendText("message [" + Objects.toString(message, "n/a") + "]");
    }

    public LogEventMatcher withLevel(String level) {
        this.level = level;
        return this;
    }

    public LogEventMatcher withLogger(String logger) {
        this.logger = logger;
        return this;
    }

    public LogEventMatcher withMessage(Matcher<String> message) {
        this.message = message;
        return this;
    }

    private boolean match(LogEvent target) {
        if (level != null) {
            if (!level.equals(target.getLevel())) {
                return false;
            }
        }

        if (logger != null) {
            if (!logger.equals(target.getLogger())) {
                return false;
            }
        }

        if (message != null) {
            if (!message.matches(target.getMessage())) {
                return false;
            }
        }

        return true;
    }
}
