package org.apache.camel.component.schematron.engine;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.io.File;

/**
 * Created by akhettar on 22/12/2013.
 */
public class URIResolverImp implements URIResolver {


    private String RULES_DIR;

    /**
     * Constructor setter for rules directory path.
     * @param RULES_DIR
     */
    public URIResolverImp(final String RULES_DIR)
    {
        this.RULES_DIR = RULES_DIR;
    }

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        return new StreamSource(new File(RULES_DIR.concat(File.separator).concat(href)));
    }
}
