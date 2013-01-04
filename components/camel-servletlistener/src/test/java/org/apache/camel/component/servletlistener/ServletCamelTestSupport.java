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
package org.apache.camel.component.servletlistener;

import java.io.InputStream;

import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.servletunit.ServletRunner;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for unit testing.
 */
public abstract class ServletCamelTestSupport extends TestSupport {
    public static final String CONTEXT = "/mycontext";
    public static final String CONTEXT_URL = "http://localhost/mycontext";
    protected ServletRunner sr;

    @Before
    public void setUp() throws Exception {
        InputStream is =  this.getClass().getResourceAsStream(getConfiguration());
        assertNotNull("The configuration input stream should not be null", is);
        sr = new ServletRunner(is, CONTEXT);

        HttpUnitOptions.setExceptionsThrownOnErrorStatus(true);
    }

    @After
    public void tearDown() throws Exception {
        if (sr != null) {
            sr.shutDown();
        }
    }

    /**
     * @return The web.xml to use for testing.
     */
    protected abstract String getConfiguration();

    protected ServletCamelContext getCamelContext() {
        return CamelServletContextListener.instance;
    }

}
