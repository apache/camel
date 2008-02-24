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
package org.apache.camel.spring.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.InitializingBean;

import static org.apache.camel.util.ObjectHelper.name;

/**
 * A simple helper bean for running main classes from within the spring.xml
 * usually asynchronous in a background thread; which is useful for demos such
 * as running Swing programs in the same JVM.
 * 
 * @version $Revision$
 */
public class MainRunner implements InitializingBean, Runnable {
    private static final Log LOG = LogFactory.getLog(MainRunner.class);

    private Class main;
    private String[] args = {};
    private boolean asyncRun = true;
    private long delay;

    public String toString() {
        return "MainRunner(" + name(main) + " " + Arrays.asList(getArgs()) + ")";
    }

    public void run() {
        try {
            runMethodWithoutCatchingExceptions();
        } catch (NoSuchMethodException e) {
            LOG.error("Class: " + name(main) + " does not have a main method: " + e, e);
        } catch (IllegalAccessException e) {
            LOG.error("Failed to run: " + this + ". Reason: " + e, e);
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            LOG.error("Failed to run: " + this + ". Reason: " + throwable, throwable);
        }
    }

    public void runMethodWithoutCatchingExceptions() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                LOG.info("Caught: " + e, e);
            }
        }
        Method method = main.getMethod("main", String[].class);
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("The main method is not static!: " + method);
        }
        Object[] arguments = {getArgs()};
        method.invoke(null, arguments);
    }

    public String[] getArgs() {
        return args;
    }

    public void setArgs(String[] args) {
        this.args = args;
    }

    public boolean isAsyncRun() {
        return asyncRun;
    }

    public void setAsyncRun(boolean asyncRun) {
        this.asyncRun = asyncRun;
    }

    public Class getMain() {
        return main;
    }

    public void setMain(Class main) {
        this.main = main;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public void afterPropertiesSet() throws Exception {
        if (main == null) {
            throw new IllegalArgumentException("You must specify a main class!");
        }
        if (isAsyncRun()) {
            Thread thread = new Thread(this, "Thread for: " + this);
            thread.start();
        } else {
            runMethodWithoutCatchingExceptions();
        }
    }
}
