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
package org.apache.camel.itest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.test.karaf.AbstractFeatureTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

/**
 * CAMEL-10817: dumpModelAsXml can return invalid XML namespace xmlns:xmlns
 */

@RunWith(PaxExam.class)
public class DuplicateNamespacePrefixIssueTest extends AbstractFeatureTest {

    @Test
    public void testRoutesNamespacePrefixesNotDuplicated() throws Exception {
        CamelContext context = new BlueprintCamelContext(bundleContext, blueprintContainer);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").id("foo")
                    .choice()
                        .when(xpath("foo:foo/foo:foo = 'foo'"))
                            .log("Matched foo")
                        .when(xpath("foo:foo/foo:bar = 'bar'"))
                            .log("Matched bar")
                        .when(xpath("foo:foo/foo:cheese = 'cheese'"))
                            .log("Matched cheese");
            }
        });

        // Dump the model XML
        String originalModelXML = ModelHelper.dumpModelAsXml(context, context.getRouteDefinition("foo"));

        // Reload routes from dumped XML
        InputStream stream = new ByteArrayInputStream(originalModelXML.getBytes("UTF-8"));
        RoutesDefinition routesDefinition = ModelHelper.loadRoutesDefinition(context, stream);

        // Verify namespaces are as we expect
        String modifiedModelXML = ModelHelper.dumpModelAsXml(context, routesDefinition);
        String modifiedRoutesElementXML = modifiedModelXML.split("\n")[1];
        String expectedRoutesElementXML = "<routes xmlns=\"http://camel.apache.org/schema/spring\">";
        Assert.assertEquals(expectedRoutesElementXML, modifiedRoutesElementXML);
    }

    @Configuration
    public Option[] configure() {
        return AbstractFeatureTest.configure();
    }
}
