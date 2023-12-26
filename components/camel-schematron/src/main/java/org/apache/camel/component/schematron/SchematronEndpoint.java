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
package org.apache.camel.component.schematron;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.schematron.constant.Constants;
import org.apache.camel.component.schematron.exception.SchematronConfigException;
import org.apache.camel.component.schematron.processor.ClassPathURIResolver;
import org.apache.camel.component.schematron.processor.TemplatesFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.schematron.constant.Constants.LINE_NUMBERING;
import static org.apache.camel.component.schematron.constant.Constants.SAXON_TRANSFORMER_FACTORY_CLASS_NAME;

/**
 * Validate XML payload using the Schematron Library.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "schematron", title = "Schematron", syntax = "schematron:path",
             remote = false, producerOnly = true, category = { Category.VALIDATION })
public class SchematronEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(SchematronEndpoint.class);

    private TransformerFactory transformerFactory;

    @UriPath
    @Metadata(required = true, supportFileReference = true)
    private String path;
    @UriParam
    private boolean abort;
    @UriParam
    private Templates rules;
    @UriParam(label = "advanced")
    private URIResolver uriResolver;

    public SchematronEndpoint() {
    }

    public SchematronEndpoint(String uri, String path, SchematronComponent component) {
        super(uri, component);
        this.path = path;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SchematronProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    public String getPath() {
        return path;
    }

    /**
     * The path to the schematron rules file. Can either be in class path or location in the file system.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Flag to abort the route and throw a schematron validation exception.
     */
    public void setAbort(boolean abort) {
        this.abort = abort;
    }

    public boolean isAbort() {
        return abort;
    }

    public Templates getRules() {
        return rules;
    }

    /**
     * To use the given schematron rules instead of loading from the path
     */
    public void setRules(Templates rules) {
        this.rules = rules;
    }

    /**
     * Set the {@link URIResolver} to be used for resolving schematron includes in the rules file.
     */
    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    public URIResolver getUriResolver() {
        return uriResolver;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (transformerFactory == null) {
            createTransformerFactory();
        }

        if (rules == null) {
            try {
                // Attempt to read the schematron rules from the class path first.
                LOG.debug("Reading schematron rules from class path {}", path);
                InputStream schRules = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), path);
                rules = TemplatesFactory.newInstance().getTemplates(schRules, transformerFactory);
            } catch (Exception classPathException) {
                // Attempts from the file system.
                LOG.debug("Error loading schematron rules from class path, attempting file system {}", path);
                try {
                    InputStream schRules = FileUtils.openInputStream(new File(path));
                    rules = TemplatesFactory.newInstance().getTemplates(schRules, transformerFactory);
                } catch (FileNotFoundException e) {
                    LOG.debug("Schematron rules not found in the file system {}", path);
                    throw classPathException; // Can be more meaningful, for example, xslt compilation error.
                }
            }

            // rules not found in class path nor in file system.
            if (rules == null) {
                LOG.error("Failed to load schematron rules {}", path);
                throw new SchematronConfigException("Failed to load schematron rules: " + path);
            }
        }
    }

    private void createTransformerFactory() throws ClassNotFoundException {
        // provide the class loader of this component to work in OSGi environments
        Class<TransformerFactory> factoryClass
                = getCamelContext().getClassResolver().resolveMandatoryClass(SAXON_TRANSFORMER_FACTORY_CLASS_NAME,
                        TransformerFactory.class, SchematronComponent.class.getClassLoader());

        LOG.debug("Using TransformerFactoryClass {}", factoryClass);
        transformerFactory = getCamelContext().getInjector().newInstance(factoryClass);
        transformerFactory.setURIResolver(new ClassPathURIResolver(Constants.SCHEMATRON_TEMPLATES_ROOT_DIR, this.uriResolver));
        transformerFactory.setAttribute(LINE_NUMBERING, true);
    }

}
