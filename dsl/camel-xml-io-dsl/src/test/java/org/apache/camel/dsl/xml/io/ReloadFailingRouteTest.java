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

package org.apache.camel.dsl.xml.io;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.xml.io.XmlPullParserLocationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReloadFailingRouteTest {

    @Test
    public void testReload() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        String invalidRoute = "<route id=\"test\">\n" + "    <from uri=\"seda:xml\"/>\n"
                + "    <log message=\"Some valid message\"/>\n"
                + "\t<setHeader name=\"SomeHeader\">\n"
                + "\t\t<toosimple>XYZ</toosimple>\n"
                + "\t</setHeader>\n"
                + "</route>";

        String validRoute = "<route id=\"test\">\n" + "    <from uri=\"seda:xml\"/>\n"
                + "    <log message=\"Some valid message\"/>\n"
                + "\t<setHeader name=\"SomeHeader\">\n"
                + "\t\t<simple>XYZ</simple>\n"
                + "\t</setHeader>\n"
                + "</route>";

        Resource invalidResource = ResourceHelper.fromString("dummy.xml", invalidRoute);
        Resource validResource = ResourceHelper.fromString("dummy.xml", validRoute);

        RoutesLoader loader = PluginHelper.getRoutesLoader(context);
        try {
            loader.updateRoutes(invalidResource);
            Assertions.fail();
        } catch (Exception e) {
            // expected
            Assertions.assertInstanceOf(XmlPullParserLocationException.class, e);
        }

        loader.updateRoutes(validResource);

        Assertions.assertEquals(1, context.getRoutes().size());

        context.stop();
    }
}
