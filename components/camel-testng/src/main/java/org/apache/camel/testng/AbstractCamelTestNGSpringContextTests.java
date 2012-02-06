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
package org.apache.camel.testng;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.camel.test.spring.CamelSpringTestContextLoader;
import org.apache.camel.test.spring.CamelSpringTestContextLoaderTestExecutionListener;
import org.apache.camel.test.spring.DisableJmxTestExecutionListener;
import org.apache.camel.test.spring.StopWatchTestExecutionListener;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 * Bridges Camel testing capabilities from {@link CamelSpringTestSupport} into
 * Spring Test driven classes. This class is based on {@link AbstractTestNGSpringContextTests}
 * but must re-implement much of the logic due to a lack of extensibility in
 * {@code AbstractTestNGSpringContextTests}. Specifically the need to inject a different default
 * {@link ContextLoader} implementation and to prepend {@link TestExecutionListeners} to the list
 * defined on {@code AbstractTestNGSpringContextTests}.
 */
@TestExecutionListeners(
                        listeners = {
                                     CamelSpringTestContextLoaderTestExecutionListener.class,
                                     DependencyInjectionTestExecutionListener.class,
                                     DirtiesContextTestExecutionListener.class,
                                     TransactionalTestExecutionListener.class,
                                     DisableJmxTestExecutionListener.class,
                                     StopWatchTestExecutionListener.class})
public abstract class AbstractCamelTestNGSpringContextTests 
        implements IHookable, ApplicationContextAware {

    protected ApplicationContext applicationContext;

    private final TestContextManager testContextManager;

    private Throwable testException;

    /**
     * Construct a new AbstractTestNGSpringContextTests instance and initialize
     * the internal {@link TestContextManager} for the current test.
     */
    public AbstractCamelTestNGSpringContextTests() {
        this.testContextManager = new TestContextManager(getClass(), getDefaultContextLoaderClassName(getClass()));
    }

   /**
    * Set the {@link ApplicationContext} to be used by this test instance,
    * provided via {@link ApplicationContextAware} semantics.
    * 
    * @param applicationContext the applicationContext to set
    */
    public final void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
   
   /**
    * Returns the specialized loader for tight integration between Camel testing features
    * and the application context initialization.
    * 
    * @return Returns the class name for {@link CamelSpringTestContextLoader}
    */
    protected String getDefaultContextLoaderClassName(Class<?> clazz) {
        return CamelSpringTestContextLoader.class.getName();
    }

    @BeforeClass(alwaysRun = true)
    protected void springTestContextPrepareTestInstance() throws Exception {
        this.testContextManager.beforeTestClass();
        this.testContextManager.prepareTestInstance(this);
    }

    @BeforeMethod(alwaysRun = true)
    protected void springTestContextBeforeTestMethod(Method testMethod) throws Exception {
        this.testContextManager.beforeTestMethod(this, testMethod);
    }

    public void run(IHookCallBack callBack, ITestResult testResult) {
        callBack.runTestMethod(testResult);

        Throwable testResultException = testResult.getThrowable();
        if (testResultException instanceof InvocationTargetException) {
            testResultException = ((InvocationTargetException)testResultException).getCause();
        }
        this.testException = testResultException;
    }

    @AfterMethod(alwaysRun = true)
    protected void springTestContextAfterTestMethod(Method testMethod) throws Exception {
        try {
            this.testContextManager.afterTestMethod(this, testMethod, this.testException);
        } finally {
            this.testException = null;
        }
    }

    @AfterClass(alwaysRun = true)
    protected void springTestContextAfterTestClass() throws Exception {
        this.testContextManager.afterTestClass();
    }
}

