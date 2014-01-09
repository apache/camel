package org.apache.camel.component.schematron.engine;

import net.sf.saxon.lib.FeatureKeys;
import org.apache.camel.component.schematron.exception.SchematronConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.InputStream;

/**
 * Created by akhettar on 20/12/2013.
 */
public final class TemplatesFactory {


    private Logger logger = LoggerFactory.getLogger(TemplatesFactory.class);
    private static TemplatesFactory fac;
    private static final String ROOT_DIR = "iso-schematron-xslt2";
    private static final String[] PIPELINE = new String[]{"iso_dsdl_include.xsl", "iso_abstract_expand.xsl", "iso_svrl_for_xslt2.xsl"};
    private Templates templates;


    /**
     * Creates singleton instance of the templates factory
     *
     * @param rules the path to the rules file: can be either class or file  based path.
     * @return
     */
    public static TemplatesFactory newInstance(InputStream rules) {

        if (fac != null) {
            return fac;
        }
        return new TemplatesFactory(rules);
    }

    /**
     * private constructor.
     *
     * @param rules
     */
    private TemplatesFactory(InputStream rules) {

        // create new instance.
        TransformerFactory fac = TransformerFactory.newInstance();
        fac.setURIResolver(new ClassPathURIResolver(ROOT_DIR));
        fac.setAttribute(FeatureKeys.LINE_NUMBERING, true);
        Node node = null;
        Transformer t = null;
        Source source = new StreamSource(rules);
        try {
            for (String template : PIPELINE) {
                Source xsl = new StreamSource(ClassLoader.getSystemResourceAsStream(ROOT_DIR.concat(File.separator).concat(template)));
                t = fac.newTransformer(xsl);
                DOMResult result = new DOMResult();
                t.transform(source, result);
                source = new DOMSource(node = result.getNode());
            }
            templates = fac.newTemplates(new DOMSource(node));
        } catch (TransformerConfigurationException e) {
            logger.error(e.getMessage(), e);
            throw new SchematronConfigException(e);
        } catch (TransformerException e) {
            logger.error(e.getMessage(), e);
            throw new SchematronConfigException(e);
        }

    }

    /**
     * Returns an instance of compiled schematron templates.
     *
     * @return
     */
    public Templates newTemplates() {
        return templates;
    }
}
