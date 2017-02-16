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
import org.apache.camel.component.language.LanguageComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class LanguageComponentConfigurationAndDocumentationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testComponentConfiguration() throws Exception {
        LanguageComponent comp = context.getComponent("language", LanguageComponent.class);
        EndpointConfiguration conf = comp.createConfiguration("language:simple:foo?transform=false");

        assertEquals("false", conf.getParameter("transform"));

        ComponentConfiguration compConf = comp.createComponentConfiguration();
        String json = compConf.createParameterJsonSchema();
        assertNotNull(json);

        assertTrue(json.contains("\"languageName\": { \"kind\": \"path\", \"displayName\": \"Language Name\", \"group\": \"producer\", \"required\": true"));
        assertTrue(json.contains("\"script\": { \"kind\": \"parameter\", \"displayName\": \"Script\", \"group\": \"producer\", \"type\": \"string\""));
    }

}
