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
package org.apache.camel.component.schematron;

import java.io.File;
import java.io.InputStream;

import javax.xml.transform.Templates;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.schematron.exception.SchematronConfigException;
import org.apache.camel.component.schematron.processor.TemplatesFactory;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ResourceHelper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




/**
 * Schematron Endpoint.
 */
public class SchematronEndpoint extends DefaultEndpoint {

    private Logger logger = LoggerFactory.getLogger(SchematronEndpoint.class);
    private String remaining;
    private boolean abort;
    private Templates rules;


    public SchematronEndpoint() {
    }

    public SchematronEndpoint(String uri, String remaining, SchematronComponent component) {
        super(uri, component);
        this.remaining = remaining;
    }

    public SchematronEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new SchematronProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    public boolean isSingleton() {
        return true;
    }

    public void setAbort(boolean abort) {
        this.abort = abort;
    }

    public boolean isAbort() {
        return abort;
    }

    public Templates getRules() {
        return rules;
    }

    public void setRules(Templates rules) {
        this.rules = rules;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (rules == null) {
            try {
                // Attempt to read the schematron rules  from the class path first.
                logger.info("Reading schematron rules from class path {}", remaining);
                InputStream schRules = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), remaining);
                rules = TemplatesFactory.newInstance().newTemplates(schRules);
            } catch (Exception e) {
                // Attempts from the file system.
                logger.info("Schamatron rules not found in class path, attempting file system {}", remaining);
                InputStream schRules = FileUtils.openInputStream(new File(remaining));
                rules = TemplatesFactory.newInstance().newTemplates(schRules);
            }

            // rules not found in class path nor in file system.
            if (rules == null) {
                logger.error("Schematron rules not found {}", remaining);
                throw new SchematronConfigException("Failed to load rules: " + remaining);
            }
        }

    }
}
