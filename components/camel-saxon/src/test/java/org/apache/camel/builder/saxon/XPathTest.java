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
package org.apache.camel.builder.saxon;

import javax.xml.xpath.XPathFactory;

import net.sf.saxon.xpath.XPathFactoryImpl;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class XPathTest extends CamelTestSupport {

    @Test
    public void testXPathUsingSaxon() {
        XPathFactory fac = new XPathFactoryImpl();
        XPathBuilder builder = XPathBuilder.xpath("foo/bar").factory(fac);

        // will evaluate as XPathConstants.NODESET and have Camel convert that to String
        // this should return the String incl. xml tags
        String name = builder.evaluate(context, "<foo><bar id=\"1\">cheese</bar></foo>", String.class);
        assertEquals("<bar id=\"1\">cheese</bar>", name);

        // will evaluate using XPathConstants.STRING which just return the text content (eg like text())
        name = builder.evaluate(context, "<foo><bar id=\"1\">cheese</bar></foo>");
        assertEquals("cheese", name);
    }

    @Test
    public void testXPathFunctionSubstringUsingSaxon() {
        String xml = "<foo><bar>Hello World</bar></foo>";

        XPathFactory fac = new XPathFactoryImpl();
        XPathBuilder builder = XPathBuilder.xpath("substring(/foo/bar, 7)").factory(fac);

        String result = builder.resultType(String.class).evaluate(context, xml, String.class);
        assertEquals("World", result);

        result = builder.evaluate(context, xml);
        assertEquals("World", result);
    }

    @Test
    public void testXPathFunctionTokenizeUsingSaxonXPathFactory() {
        // START SNIPPET: e1
        // create a Saxon factory
        XPathFactory fac = new net.sf.saxon.xpath.XPathFactoryImpl();

        // create a builder to evaluate the xpath using the saxon factory
        XPathBuilder builder = XPathBuilder.xpath("tokenize(/foo/bar, '_')[2]").factory(fac);

        // evaluate as a String result
        String result = builder.evaluate(context, "<foo><bar>abc_def_ghi</bar></foo>");
        assertEquals("def", result);
        // END SNIPPET: e1
    }

    @Test
    public void testXPathFunctionTokenizeUsingSaxon() {
        // START SNIPPET: e3
        // create a builder to evaluate the xpath using saxon
        XPathBuilder builder = XPathBuilder.xpath("tokenize(/foo/bar, '_')[2]").saxon();

        // evaluate as a String result
        String result = builder.evaluate(context, "<foo><bar>abc_def_ghi</bar></foo>");
        assertEquals("def", result);
        // END SNIPPET: e3
    }

}
