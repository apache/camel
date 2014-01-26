package org.apache.camel.component.schematron.util;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.commons.lang.StringUtils;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility Class.
 * <p/>
 * Created by akhettar on 29/12/2013.
 */
public final class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);


    /**
     * Evaluate an XPATH expression.
     *
     * @param xpath
     * @param xml
     * @return
     */
    public static String evaluate(final String xpath, final String xml) {
        Map m = new HashMap();
        m.put("svrl", Constants.HTTP_PURL_OCLC_ORG_DSDL_SVRL);

        org.custommonkey.xmlunit.NamespaceContext ctx = new SimpleNamespaceContext(m);
        XpathEngine xpathEngine = XMLUnit.newXpathEngine();
        xpathEngine.setNamespaceContext(ctx);

        try {
            return xpathEngine.evaluate(xpath, XMLUnit.buildControlDocument(xml));
        } catch (Exception e) {
            logger.error("Failed to apply xpath {} on xml {}", xpath, xml);
            throw new RuntimeCamelException(e);
        }
    }


    /**
     * Get validation status SUCCESS OR FAILURE if there are any schematron validation errors.
     * @param report
     * @return
     */
    public static String getValidationStatus(final String report)
    {
        return StringUtils.contains(report,
                Constants.FAILED_ASSERT)? Constants.FAILED : Constants.SUCCESS;
    }
}
