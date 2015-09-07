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
package org.apache.camel.component.xslt;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ObjectHelper;

public class XsltRouteXsltWithErrorTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testXsltWithError() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("xslt:org/apache/camel/component/xslt/transform-with-error.xsl");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            TransformerConfigurationException cause = ObjectHelper.getException(TransformerConfigurationException.class, e);
            assertNotNull(cause);
            // not sure if XSLT errors may be i18n and not english always so just check for the spelling mistake of select -> slect
            assertTrue(cause.getMessage().contains("slect"));
        }
    }

}
