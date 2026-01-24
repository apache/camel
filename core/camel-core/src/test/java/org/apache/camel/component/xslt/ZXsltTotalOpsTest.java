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

import javax.xml.transform.TransformerConfigurationException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
public class ZXsltTotalOpsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testXsltTotalOps() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                XsltComponent xslt = context.getComponent("xslt", XsltComponent.class);
                xslt.setXpathTotalOpLimit(1);

                from("direct:start")
                        .to("xslt:org/apache/camel/component/xslt/example.xsl?output=bytes").to("mock:result");
            }
        });

        Exception e = assertThrows(Exception.class, () -> context.start(),
                "Should fail due to low total ops");

        TransformerConfigurationException tce
                = assertIsInstanceOf(TransformerConfigurationException.class, e.getCause().getCause().getCause());
        assertTrue(tce.getMessage().endsWith("operators that exceeds the '1' limit set by 'system property'."));
    }

}
