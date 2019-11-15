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
package org.apache.camel.component.schematron.processor;

import java.io.InputStream;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Node;

import org.apache.camel.component.schematron.constant.Constants;
import org.apache.camel.component.schematron.exception.SchematronConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class generating Templates for a given schematron rules
 */
public final class TemplatesFactory {


    private static final TemplatesFactory INSTANCE = new TemplatesFactory();
    private static final String[] PIPELINE = new String[]{"iso_dsdl_include.xsl", "iso_abstract_expand.xsl", "iso_svrl_for_xslt2.xsl"};
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
     * Generate the schematron template for given rule.
     *
     * @param rules the schematron rules
     * @param fac   the transformer factory.
     * @return schematron template.
     */
    public Templates getTemplates(final InputStream rules, final TransformerFactory fac) {

        Node node = null;
        Source source = new StreamSource(rules);
        try {
            for (String template : PIPELINE) {
                String path = Constants.SCHEMATRON_TEMPLATES_ROOT_DIR
                        .concat("/").concat(template);
                InputStream xsl =  this.getClass().getClassLoader().getResourceAsStream(path);
                Transformer t = fac.newTransformer(new StreamSource(xsl));
                DOMResult result = new DOMResult();
                t.transform(source, result);
                source = new DOMSource(node = result.getNode());
            }
            return fac.newTemplates(new DOMSource(node));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new SchematronConfigException(e);
        }
    }
}
