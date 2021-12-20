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
package org.apache.camel.dsl.jbang.core.templates;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

import org.apache.camel.CamelException;
import org.apache.camel.dsl.jbang.core.api.TemplateParser;
import org.apache.camel.dsl.jbang.core.common.exceptions.ResourceAlreadyExists;
import org.apache.camel.util.ObjectHelper;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.RuntimeConstants;

public class VelocityTemplateParser implements TemplateParser {
    private final Properties properties = new Properties();

    public VelocityTemplateParser(File templateDir, String propertiesFile) throws IOException {
        this(templateDir, new File(propertiesFile));
    }

    public VelocityTemplateParser(File templateDir, File propertiesFile) throws IOException {
        initializeTemplateEngine(templateDir);

        try (FileReader propertiesReader = new FileReader(propertiesFile)) {
            properties.load(propertiesReader);
        }
    }

    private void initializeTemplateEngine(File templateDir) {
        Properties props = new Properties();

        props.setProperty(RuntimeConstants.RESOURCE_LOADER, "file");

        props.put("resource.loader.file.path", templateDir.getAbsolutePath());

        Velocity.init(props);
    }

    private void overridePropertyList(VelocityContext context, Properties properties, String requiredKameletProperties) {
        String requiredPropertyList = properties.getProperty(requiredKameletProperties);

        if (ObjectHelper.isNotEmpty(requiredPropertyList)) {
            context.put(requiredKameletProperties, requiredPropertyList.split(","));
        }
    }

    @Override
    public void parse(String templateFileName, Writer writer) throws CamelException {
        VelocityContext context = new VelocityContext();

        try {
            loadTemplateProperties(context);
        } catch (IOException e) {
            throw new CamelException("Unable to load the template properties", e);
        }

        try {
            Template template = Velocity.getTemplate(templateFileName);

            template.merge(context, writer);
        } catch (ResourceNotFoundException rnfe) {
            throw new CamelException("Could not find the template to parse", rnfe);
        } catch (ParseErrorException pee) {
            throw new CamelException("Failed parsing the template", pee);
        } catch (MethodInvocationException mie) {
            throw new CamelException("Method call within the templated has failed", mie);
        } catch (Exception e) {
            throw new CamelException("Unspecified error while loading, parsing or processing the template", e);
        }
    }

    private void loadTemplateProperties(VelocityContext context) throws IOException {
        properties.forEach((k, v) -> context.put(k.toString(), v));

        overridePropertyList(context, properties, "kameletProperties");
        overridePropertyList(context, properties, "requiredKameletProperties");
        overridePropertyList(context, properties, "kameletBeans");
        overridePropertyList(context, properties, "fromParameters");
        overridePropertyList(context, properties, "toParameters");
    }

    public File getOutputFile(File outputDir) throws ResourceAlreadyExists {
        String outputFileName = properties.getProperty("kameletMetadataName") + ".kamelet.yaml";

        File outputFile = new File(outputDir, outputFileName);
        if (outputFile.exists()) {
            throw new ResourceAlreadyExists(outputFile);
        }

        return outputFile;
    }
}
