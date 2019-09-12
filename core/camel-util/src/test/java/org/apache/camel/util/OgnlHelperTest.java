package org.apache.camel.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class OgnlHelperTest extends Assert {

    /**
     * Tests correct splitting in case the OGNL expression contains method parameters with brackets.
     */
    @Test
    public void splitOgnlWithRegexInMethod() {
        String ognl = "header.cookie.replaceFirst(\".*;?iwanttoknow=([^;]+);?.*\", \"$1\")";
        assertFalse(OgnlHelper.isInvalidValidOgnlExpression(ognl));
        assertTrue(OgnlHelper.isValidOgnlExpression(ognl));

        List<String> strings = OgnlHelper.splitOgnl(ognl);
        assertEquals(3, strings.size());
        assertEquals("header", strings.get(0));
        assertEquals(".cookie", strings.get(1));
        assertEquals(".replaceFirst(\".*;?iwanttoknow=([^;]+);?.*\", \"$1\")", strings.get(2));
    }

}
