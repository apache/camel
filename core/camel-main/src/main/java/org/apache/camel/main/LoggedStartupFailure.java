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
package org.apache.camel.main;

/**
 * The uncaught exception handler of the thread that started Camel, once Camel failed to start and the failure was
 * logged: the JVM does not print that failure a second time when it leaves main, also not when a launcher wraps it (the
 * launcher of a fat jar calls main by reflection, and wraps the failure in an InvocationTargetException). Any other
 * exception goes to the handler the thread had before, which prints it as the JVM does.
 * <p/>
 * Spring Boot does the same for an application that fails to start.
 */
final class LoggedStartupFailure implements Thread.UncaughtExceptionHandler {

    private static final int MAX_CAUSES = 32;

    private final Thread.UncaughtExceptionHandler before;
    private final Throwable logged;

    private LoggedStartupFailure(Thread.UncaughtExceptionHandler before, Throwable logged) {
        this.before = before;
        this.logged = logged;
    }

    /**
     * The failure was logged: the current thread does not print it again when the failure leaves the thread.
     */
    static void skipWhenUncaught(Throwable logged) {
        Thread thread = Thread.currentThread();
        // the handler of the thread, else its thread group, which prints an exception as the JVM does
        Thread.UncaughtExceptionHandler before = thread.getUncaughtExceptionHandler();
        if (before instanceof LoggedStartupFailure earlier) {
            before = earlier.before;
        }
        thread.setUncaughtExceptionHandler(new LoggedStartupFailure(before, logged));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable e) {
        if (!isLogged(e)) {
            before.uncaughtException(thread, e);
        }
    }

    // the logged failure, or an exception that wraps it
    boolean isLogged(Throwable e) {
        Throwable t = e;
        for (int i = 0; t != null && i < MAX_CAUSES; i++) {
            if (t == logged) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
