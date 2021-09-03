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
package org.apache.camel.component.activemq.support;

import java.nio.file.Path;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;

public abstract class ActiveMQSpringTestSupport extends CamelSpringTestSupport implements ActiveMQSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return newAppContext(getClass().getSimpleName() + "-context.xml");
    }

    public Path testDirectory() {
        return CamelTestSupport.testDirectory(getClass(), false);
    }

    protected int getShutdownTimeout() {
        return 1;
    }
}
