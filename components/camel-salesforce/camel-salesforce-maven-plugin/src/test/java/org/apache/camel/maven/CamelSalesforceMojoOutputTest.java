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
package org.apache.camel.maven;

import java.io.File;
import java.io.InputStream;
import java.lang.Exception;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

import org.junit.Test;
import org.junit.Assert;
import org.junit.Before;

import org.apache.maven.plugin.logging.SystemStreamLog;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;

import org.apache.log4j.Logger;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.log.Log4JLogChute;

import org.apache.commons.io.FileUtils;

//import java.util.Scanner;

public class CamelSalesforceMojoOutputTest {
    private static final String TEST_CASE_FILE = "case.json";
    private static final String TEST_CALCULATED_FORMULA_FILE = "complex_calculated_formula.json";
    private static final String OUTPUT_FOLDER = "target/test-generated-sources";
    private static final String generatedDate = "Thu Mar 09 16:15:49 ART 2017";

    private CamelSalesforceMojoAccessor mojo;
    private CamelSalesforceMojo.GeneratorUtility utility;
    private File pkgDir;

    private static class CamelSalesforceMojoAccessor extends CamelSalesforceMojo {
        private static final Logger LOG = Logger.getLogger(CamelSalesforceMojoAccessor.class.getName());

        public CamelSalesforceMojoAccessor() throws Exception {
            // initialize velocity to load resources from class loader and use Log4J
            Properties velocityProperties = new Properties();
            velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADER, "cloader");
            velocityProperties.setProperty("cloader.resource.loader.class", ClasspathResourceLoader.class.getName());
            velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, Log4JLogChute.class.getName());
            velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM + ".log4j.logger", LOG.getName());

            VelocityEngine engine = new VelocityEngine(velocityProperties);
            engine.init();

            Field field = CamelSalesforceMojo.class.getDeclaredField("engine");
            field.setAccessible(true);
            field.set(this, engine);
        }

        // Expose processDescription in order to test it
        public void processDescription(File pkgDir, SObjectDescription description, GeneratorUtility utility, String generatedDate) throws Exception {
            Method method = CamelSalesforceMojo.class.getDeclaredMethod("processDescription", File.class, SObjectDescription.class, CamelSalesforceMojo.GeneratorUtility.class, String.class);
            method.setAccessible(true);
            method.invoke(this, pkgDir, description, utility, generatedDate);
        }
    }

    @Before
    public void setUp() throws Exception {
        mojo = createMojo();

        pkgDir = new File(OUTPUT_FOLDER);
        if (!pkgDir.exists()) {
            if (!pkgDir.mkdirs()) {
                throw new Exception("Unable to create " + pkgDir);
            }
        }

        utility = createGeneratorUtility();
    }

    @Test
    public void testProcessDescriptionPickLists() throws Exception {
        SObjectDescription description = createSObjectDescription(TEST_CASE_FILE);

        mojo.processDescription(pkgDir, description, utility, generatedDate);

        assertClassFile("Case.java");
        assertClassFile("Case_PickListAccentMarkEnum.java");
        assertClassFile("Case_PickListQuotationMarkEnum.java");
        assertClassFile("Case_PickListSlashEnum.java");
        assertClassFile("QueryRecordsCase.java");
    }

    @Test
    public void testProcessDescriptionCalculatedFormula() throws Exception {
        SObjectDescription description = createSObjectDescription(TEST_CALCULATED_FORMULA_FILE);

        mojo.processDescription(pkgDir, description, utility, generatedDate);

        assertClassFile("ComplexCalculatedFormula.java");
        assertClassFile("QueryRecordsComplexCalculatedFormula.java");
    }

    public void assertClassFile(String name) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Assert.assertTrue("Class "+name+" must be equal to the target one", FileUtils.contentEquals(new File(OUTPUT_FOLDER, name), FileUtils.toFile(classLoader.getResource("generated/"+name))));
    }

    protected CamelSalesforceMojo.GeneratorUtility createGeneratorUtility() {
        return new CamelSalesforceMojo.GeneratorUtility(false);
    }

    protected SObjectDescription createSObjectDescription(String name) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(name);
        ObjectMapper mapper = JsonUtils.createObjectMapper();
        SObjectDescription description = mapper.readValue(inputStream, SObjectDescription.class);
        if (description == null)
            throw new Exception("Couldn't Read description from file");

        return description;
    }

    protected CamelSalesforceMojoAccessor createMojo() throws Exception {
        CamelSalesforceMojoAccessor mojo = new CamelSalesforceMojoAccessor();
        mojo.setLog(new SystemStreamLog());
        return mojo;
    }
}
