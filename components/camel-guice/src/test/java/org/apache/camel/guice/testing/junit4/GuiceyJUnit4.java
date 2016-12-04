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
package org.apache.camel.guice.testing.junit4;

import org.apache.camel.guice.testing.InjectorManager;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class GuiceyJUnit4 extends BlockJUnit4ClassRunner {

    protected static InjectorManager manager = new InjectorManager();

    public GuiceyJUnit4(Class<?> aClass) throws InitializationError {
        super(aClass);
    }

    @Override
    protected Statement withBeforeClasses(Statement statement) {
        final Statement parent = super.withBeforeClasses(statement);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                manager.beforeClasses();

                parent.evaluate();
            }
        };
    }

    @Override
    protected Statement withAfterClasses(Statement statement) {
        final Statement parent = super.withAfterClasses(statement);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                parent.evaluate();

                manager.afterClasses();
            }
        };
    }

    @Override
    protected Statement withBefores(FrameworkMethod frameworkMethod, final Object test, Statement statement) {
        final Statement parent = super.withBefores(frameworkMethod, test, statement);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                manager.beforeTest(test);

                parent.evaluate();
            }
        };
    }

    @Override
    protected Statement withAfters(FrameworkMethod frameworkMethod, final Object test, Statement statement) {
        final Statement parent = super.withBefores(frameworkMethod, test, statement);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                parent.evaluate();

                manager.afterTest(test);
            }
        };
    }

    @Override
    public void run(RunNotifier runNotifier) {
        super.run(runNotifier);
    }
}
