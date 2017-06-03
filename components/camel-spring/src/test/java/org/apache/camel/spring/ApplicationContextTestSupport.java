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
package org.apache.camel.spring;

import junit.framework.TestCase;
import org.apache.camel.util.IOHelper;
import org.springframework.context.support.AbstractXmlApplicationContext;

/**
 * @version 
 */
public abstract class ApplicationContextTestSupport extends TestCase {
    protected AbstractXmlApplicationContext applicationContext;

    protected abstract AbstractXmlApplicationContext createApplicationContext();

    @Override
    protected void setUp() throws Exception {
        applicationContext = createApplicationContext();
        assertNotNull("Should have created a valid spring context", applicationContext);

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        IOHelper.close(applicationContext);
    }


    /**
     * Looks up the mandatory spring bean of the given name and type, failing if
     * it is not present or the correct type
     */
    public <T> T getMandatoryBean(Class<T> type, String name) {
        return applicationContext.getBean(name, type);
    }
}
