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
package org.apache.camel.component.schematron.engine;

import org.apache.camel.component.schematron.util.Utils;
import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.IgnoreTextAndAttributeValuesDifferenceListener;
import org.junit.BeforeClass;
import org.junit.Test;
import javax.xml.transform.Templates;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Created by akhettar on 27/12/2013.
 */
public class SchematronEngineTest {

    private static SchematronEngine engine;

    @BeforeClass
    public static void setUP() {
        Templates rules = TemplatesFactory.newInstance().newTemplates(ClassLoader.getSystemResourceAsStream("sch/sample-schematron.sch"));
        engine = SchematronEngineFactory.newScehamtronEngine(rules);
    }

    @Test
    public void testValidXML() throws Exception {

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-1.xml"));
        String expected = IOUtils.toString(ClassLoader.getSystemResourceAsStream("result/article-1-result.xml"));

        // validate
        String result = engine.validate(payload);
        DifferenceListener myDifferenceListener = new IgnoreTextAndAttributeValuesDifferenceListener();
        Diff myDiff = new Diff(expected, result);
        myDiff.overrideDifferenceListener(myDifferenceListener);
        assertTrue(myDiff.similar());
    }

    @Test
    public void testInValidXML() throws Exception {

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));

        // validate
        String result = engine.validate(payload);

        // should throw two assertions because of the missing chapters in the XML.
        assertEquals("A chapter should have a title", Utils.evaluate("//svrl:failed-assert/svrl:text", result));


    }
}
