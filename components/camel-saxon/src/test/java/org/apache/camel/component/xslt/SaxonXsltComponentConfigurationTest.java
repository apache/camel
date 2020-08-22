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
package org.apache.camel.component.xslt;

import org.apache.camel.component.xslt.saxon.XsltSaxonComponent;
import org.apache.camel.component.xslt.saxon.XsltSaxonEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SaxonXsltComponentConfigurationTest extends CamelSpringTestSupport {
    @Test
    public void testConfiguration() throws Exception {
        XsltSaxonComponent component = context.getComponent("xslt-saxon", XsltSaxonComponent.class);
        XsltSaxonEndpoint endpoint
                = context.getEndpoint("xslt-saxon:org/apache/camel/component/xslt/transform.xsl", XsltSaxonEndpoint.class);

        assertNotNull(component.getSaxonConfiguration());
        assertNotNull(component.getSaxonConfigurationProperties());
        assertFalse(component.getSaxonConfigurationProperties().isEmpty());
        assertEquals(component.getSaxonConfiguration(), endpoint.getSaxonConfiguration());
        assertEquals(component.getSaxonConfigurationProperties(), endpoint.getSaxonConfigurationProperties());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/xslt/SaxonXsltComponentConfigurationTest.xml");
    }
}
