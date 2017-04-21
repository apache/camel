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
package org.apache.camel.itest.doc;

import org.apache.camel.ComponentConfiguration;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.component.file.remote.FtpComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class FtpComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        FtpComponent comp = context.getComponent("ftp", FtpComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("ftp://myhost?username=foo&password=secret&soTimeout=1234");

        assertEquals("foo", conf.getParameter("username"));
        assertEquals("secret", conf.getParameter("password"));
        assertEquals("1234", conf.getParameter("soTimeout"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"minDepth\": { \"kind\": \"parameter\", \"displayName\": \"Min Depth\", \"group\": \"filter\""
            + ", \"label\": \"consumer,filter\", \"type\": \"integer\", \"javaType\": \"int\""));
        assertTrue(json.contains("\"username\": { \"kind\": \"parameter\", \"displayName\": \"Username\", \"group\": \"security\""
            + ", \"label\": \"security\", \"type\": \"string\""));
    }

}
