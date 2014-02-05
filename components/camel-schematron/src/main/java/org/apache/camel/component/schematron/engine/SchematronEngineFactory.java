package org.apache.camel.component.schematron.engine;

import com.sun.org.apache.xerces.internal.util.XMLCatalogResolver;
import org.apache.camel.component.schematron.exception.SchematronConfigException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Templates;
import java.io.InputStream;

/**
 * Created by akhettar on 22/12/2013.
 */
public final class SchematronEngineFactory {

    private Logger logger = LoggerFactory.getLogger(SchematronEngineFactory.class);
    private static SchematronEngineFactory INSTANCE = new SchematronEngineFactory();
    private static final String SCHEMATRON_XSLT_DIR = "iso-schematron-xslt2//";
    private static String catalog;
    private static String rules_dir;
    private static Templates templates;


    /**
     * Singleton instance
     *
     * @return
     */
    public static SchematronEngineFactory newInstance(final InputStream rules) {
        templates = TemplatesFactory.newInstance(rules).newTemplates();
        return INSTANCE;
    }

    /**
     * Singleton instance for given catalogs and rule directory.
     *
     * @param catalogs
     * @return
     */
    public static SchematronEngineFactory newInstance(final String catalogs, final String ruleDir) {
        catalog = catalog;
        rules_dir = ruleDir;
        return INSTANCE;
    }

    /**
     * Creates an instance of SchematronEngine
     *
     * @return  an instance of SchematronEngine
     */
    public SchematronEngine newScehamtronEngine() {
        try {

            EntityResolver resolver = catalog == null ? null : getResolver(catalog);
            return new SchematronEngine(getXMLReader(resolver), rules_dir, templates);
        } catch (ParserConfigurationException e) {
            logger.error("Failed to parse the configuration file");
            throw new SchematronConfigException(e);
        } catch (SAXException e) {
            logger.error("Failed to parse the configuration file");
            throw new SchematronConfigException(e);
        }
    }

    /**
     * @param resolver
     * @return
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    private XMLReader getXMLReader(EntityResolver resolver) throws ParserConfigurationException, SAXException {
        final SAXParserFactory fac = SAXParserFactory.newInstance();
        fac.setValidating(false);
        final SAXParser parser = fac.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        if (resolver != null)
        {
            reader.setEntityResolver(resolver);
        }
        return reader;
    }

    /**
     * Creates an instance of Entity Resolver from given catalog.
     *
     * @param catlogs
     * @return
     */
    private EntityResolver getResolver(String catlogs) {

        XMLCatalogResolver resolver = new XMLCatalogResolver(StringUtils.split(catlogs, ","));
        resolver.setPreferPublic(true);
        return resolver;

    }

}
