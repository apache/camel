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
package org.apache.camel.component.controlbus;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * The <a href="http://camel.apache.org/controlbus.html">Control Bus component</a> allows sending messages to a
 * control-bus endpoint to control the lifecycle of routes.
 */
@Component("controlbus")
public class ControlBusComponent extends DefaultComponent {

    @Metadata(label = "security")
    private String allowedLanguages;

    private ExecutorService executorService;

    public ControlBusComponent() {
    }

    public String getAllowedLanguages() {
        return allowedLanguages;
    }

    /**
     * The names of the languages the language command may use, separated by comma, such as simple. The language command
     * evaluates the message body as an expression, so it is off until the language is in this list: a
     * controlbus:language endpoint whose language is not listed fails when it is created. The route actions are not
     * affected. An endpoint can set its own list.
     */
    public void setAllowedLanguages(String allowedLanguages) {
        this.allowedLanguages = allowedLanguages;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ControlBusEndpoint answer = new ControlBusEndpoint(uri, this);
        answer.setAllowedLanguages(allowedLanguages);
        setProperties(answer, parameters);

        // does the control bus use a language
        if (remaining != null && remaining.startsWith("language:")) {
            String lan = remaining.substring(9);
            if (!isAllowedLanguage(answer.getAllowedLanguages(), lan)) {
                throw new IllegalArgumentException(
                        "The language command of the control bus may not use the " + lan + " language: add " + lan
                                                   + " to the allowedLanguages option of the controlbus component or"
                                                   + " endpoint to turn it on");
            }
            answer.setLanguage(getCamelContext().resolveLanguage(lan));
        }

        return answer;
    }

    private static boolean isAllowedLanguage(String allowedLanguages, String language) {
        if (ObjectHelper.isEmpty(allowedLanguages) || ObjectHelper.isEmpty(language)) {
            return false;
        }
        for (String name : allowedLanguages.split(",")) {
            if (name.trim().equals(language)) {
                return true;
            }
        }
        return false;
    }

    ExecutorService getExecutorService() {
        lock.lock();
        try {
            if (executorService == null) {
                executorService = getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "ControlBus");
            }
            return executorService;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
        super.doStop();
    }
}
