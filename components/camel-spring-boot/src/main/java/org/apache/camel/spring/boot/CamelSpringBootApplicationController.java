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
package org.apache.camel.spring.boot;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public class CamelSpringBootApplicationController {

    private static final Logger LOG = LoggerFactory.getLogger(CamelSpringBootApplicationController.class);

    private final Main main;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean completed = new AtomicBoolean();

    public CamelSpringBootApplicationController(final ApplicationContext applicationContext, final CamelContext camelContext) {
        this.main = new Main() {
            @Override
            protected ProducerTemplate findOrCreateCamelTemplate() {
                return applicationContext.getBean(ProducerTemplate.class);
            }

            @Override
            protected Map<String, CamelContext> getCamelContextMap() {
                return Collections.singletonMap("camelContext", camelContext);
            }

            @Override
            protected void doStop() throws Exception {
                LOG.debug("Controller is shutting down CamelContext");
                try {
                    super.doStop();
                } finally {
                    completed.set(true);
                    // should use the latch on this instance
                    CamelSpringBootApplicationController.this.latch.countDown();
                }
            }
        };
    }

    public CountDownLatch getLatch() {
        return this.latch;
    }

    public AtomicBoolean getCompleted() {
        return completed;
    }

    /**
     * Runs the application and blocks the main thread and shutdown Camel graceful when the JVM is stopping.
     */
    public void run() {
        LOG.debug("Controller is starting and waiting for Spring-Boot to stop or JVM to terminate");
        try {
            main.run();
            // keep the daemon thread running
            LOG.debug("Waiting for CamelContext to complete shutdown");
            latch.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LOG.debug("CamelContext shutdown complete.");
    }

    /**
     * @deprecated use {@link #run()}
     */
    @Deprecated
    public void blockMainThread() {
        run();
    }

    @PreDestroy
    private void destroy() {
        main.completed();
    }

}