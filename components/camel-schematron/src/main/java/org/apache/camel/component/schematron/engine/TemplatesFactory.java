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


import net.sf.saxon.FeatureKeys;
import org.apache.camel.component.schematron.contant.Constants;
import org.apache.camel.component.schematron.exception.SchematronConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.InputStream;


/**
 * Class generating Templates for given schematron rules
 *
 * Created by akhettar on 20/12/2013.
 */
public final class TemplatesFactory {

    private static final  TemplatesFactory INSTANCE = new TemplatesFactory();
    private  String[] pipeline = new String[]{"iso_dsdl_include.xsl", "iso_abstract_expand.xsl", "iso_svrl_for_xslt2.xsl"};
    private Logger logger = LoggerFactory.getLogger(TemplatesFactory.class);

    /**
     * Singleton constructor;
     *
     * @return
     */
    public static TemplatesFactory newInstance() {
        return INSTANCE;
    }

    /**
     * Returns an instance of compiled schematron templates.
     *
     * @return
     */
    public Templates newTemplates(final InputStream rules) {

        // create new instance.
        Templates templates;
        TransformerFactory fac = TransformerFactory.newInstance();
        fac.setURIResolver(new ClassPathURIResolver(Constants.SCHEMATRON_TEMPLATES_ROOT_DIR));
        fac.setAttribute(FeatureKeys.LINE_NUMBERING, true);
        Node node = null;
        Transformer t = null;
        Source source = new StreamSource(rules);
        try {
            for (String template : pipeline) {
                Source xsl = new StreamSource(ClassLoader.getSystemResourceAsStream(Constants.SCHEMATRON_TEMPLATES_ROOT_DIR
                        .concat(File.separator).concat(template)));
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
        return templates;
    }

}
