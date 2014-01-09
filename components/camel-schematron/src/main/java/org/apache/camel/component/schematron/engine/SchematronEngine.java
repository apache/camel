package org.apache.camel.component.schematron.engine;

import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * The schematoron Engine. Validates an XML for given scheamtron
 * rules using an XSLT implementation of the Schematron Engine.
 *
 * Created by akhettar on 20/12/2013.
 */
public class SchematronEngine {


    private Logger logger = LoggerFactory.getLogger(SchematronEngine.class);
    private XMLReader reader;
    private String rules_dir;
    private Templates templates;


    /**
     * Constructor setting the XSLT schematron templates.
     *
     * @param reader
     * @param templates
     */
    public SchematronEngine(XMLReader reader, String rules_dir, Templates templates) {
       this.reader = reader;
       this.rules_dir = rules_dir;
       this.templates = templates;
    }


    /**
     * Validates the given XML for given Rules.
     *
     * @param xml
     * @return
     */
    public String validate(final String xml)
    {

        try {
            final Source source = new SAXSource(reader, new InputSource(IOUtils.toInputStream(xml)));
            final StringWriter writer = new StringWriter();
            final Result result = new StreamResult(writer);
            final Transformer transformer = templates.newTransformer();
            transformer.setURIResolver(new URIResolverImp(rules_dir));
            transformer.transform(source, result);
            return writer.toString();

        } catch (TransformerException e) {
            logger.error(e.getMessage());
            throw new SchematronValidationException("Failed to apply schematron validation transform", e);

        }
    }


}
