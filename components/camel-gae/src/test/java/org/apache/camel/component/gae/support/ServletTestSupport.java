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
package org.apache.camel.component.gae.support;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.meterware.servletunit.ServletRunner;
import com.meterware.servletunit.ServletUnitClient;
import org.junit.After;
import org.junit.Before;

public abstract class ServletTestSupport {

    protected static final String CTX_PATH = "";
    protected static final String CTX_URL = "http://localhost";
    
    protected static ServletRunner servletRunner;
    
    @Before
    public void setUp() throws Exception {
        // ...
    }
    
    @After
    public void tearDown() throws Exception {
        // ...
    }
    
    protected String getContentType() {
        return "text/plain; charset=UTF-8";
    }

    protected static String createUrl(String sub) {
        return CTX_URL + "/camel" + sub; 
    }
    
    protected static InputStream createInput(String data) {
        return new ByteArrayInputStream(data.getBytes());
    }
    
    protected static ServletUnitClient newClient() {
        return servletRunner.newClient();
    }

    protected static void initServlet() throws Exception {
        servletRunner.newClient().newInvocation(createUrl("")).getServlet();
    }

}
