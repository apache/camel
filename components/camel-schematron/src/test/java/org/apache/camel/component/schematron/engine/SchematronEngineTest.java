package org.apache.camel.component.schematron.engine;

import org.apache.camel.component.schematron.util.Utils;
import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.IgnoreTextAndAttributeValuesDifferenceListener;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Created by akhettar on 27/12/2013.
 */
public class SchematronEngineTest {

    private static SchematronEngine engine;

    @BeforeClass
    public static void setUP() {
        InputStream rules = ClassLoader.getSystemResourceAsStream("sch/sample-schematron.sch");
        engine = SchematronEngineFactory.newInstance(rules).newScehamtronEngine();
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
        assertEquals("A chapter should have a title", Utils.evaluate("//svrl:failed-assert [@location='/doc[1]/chapter[1]']/svrl:text", result));
        assertEquals("A chapter should have a title", Utils.evaluate("//svrl:failed-assert [@location='/doc[1]/chapter[2]']/svrl:text", result));

    }
}
