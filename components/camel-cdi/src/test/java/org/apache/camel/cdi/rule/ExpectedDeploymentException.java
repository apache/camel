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

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

public final class ExpectedDeploymentException implements TestRule {

    private final List<Matcher<Throwable>> exceptions = new ArrayList<>();

    private final List<Matcher<String>> messages = new ArrayList<>();

    private final LogVerifier log = new LogVerifier();

    private final TestRule chain;

    private ExpectedDeploymentException() {
        chain = RuleChain
            .outerRule(log)
            .around((base, description) -> new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        base.evaluate();
                    } catch (Throwable exception) {
                        assertThat(exception, allOf(pecs(exceptions)));
                        try {
                            // OpenWebBeans logs the deployment exception details
                            // TODO: OpenWebBeans only log the root cause of exception thrown in producer methods
                            //assertThat(log.getMessages(), containsInRelativeOrder(pecs(messages)))

                            List<String> causeExceptionsMessages = getCauseExceptionsMessages(exception);
                            assertThat(causeExceptionsMessages, anyOf(hasItems(messages)));
                        } catch (AssertionError error) {
                            // Weld stores the deployment exception details in the exception message
                            assertThat(exception.getMessage(), allOf(pecs(messages)));
                        }
                    }
                }
            });
    }

    private List<String> getCauseExceptionsMessages(Throwable exception) {
        List<String> exceptionsMessages = new ArrayList<>();
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
            exceptionsMessages.add(cause.getMessage());
        }
        exceptionsMessages.addAll(log.getMessages());
        return exceptionsMessages;
    }

    public static ExpectedDeploymentException none() {
        return new ExpectedDeploymentException();
    }

    public ExpectedDeploymentException expect(Class<? extends Throwable> type) {
        exceptions.add(Matchers.instanceOf(type));
        return this;
    }

    public ExpectedDeploymentException expectMessage(Matcher<String> matcher) {
        messages.add(matcher);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> List<Matcher<? super T>> pecs(List<Matcher<T>> matchers) {
        return new ArrayList<>((List) matchers);
    }

    private <T> Matcher<Iterable<? super T>>[] hasItems(List<Matcher<T>> matchers) {
        @SuppressWarnings("unchecked")
        Matcher<Iterable<? super T>>[] items = new Matcher[matchers.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = hasItem(matchers.get(i));
        }
        return items;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return chain.apply(base, description);
    }
}
