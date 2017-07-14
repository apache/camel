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
package org.apache.camel.itest.springboot;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * receives a spring-context and make it available to classes outside the spring scope.
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    private static long contextMaxWaitTime = 60000L;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        synchronized (ApplicationContextHolder.class) {
            ApplicationContextHolder.context = applicationContext;
            ApplicationContextHolder.class.notifyAll();
        }
    }

    public static ApplicationContext getApplicationContext() throws InterruptedException {
        waitForContextReady();
        return context;
    }

    private static void waitForContextReady() throws InterruptedException {
        long maxWait = contextMaxWaitTime;
        long deadline = System.currentTimeMillis() + maxWait;
        synchronized (ApplicationContextHolder.class) {
            long time = System.currentTimeMillis();
            while (time < deadline && context == null) {
                ApplicationContextHolder.class.wait(deadline - time);
                time = System.currentTimeMillis();
            }

            if (context == null) {
                throw new IllegalStateException("No spring context available after " + maxWait + " millis");
            }
        }
    }

    public static long getContextMaxWaitTime() {
        return contextMaxWaitTime;
    }

    public static void setContextMaxWaitTime(long contextMaxWaitTime) {
        ApplicationContextHolder.contextMaxWaitTime = contextMaxWaitTime;
    }

}
