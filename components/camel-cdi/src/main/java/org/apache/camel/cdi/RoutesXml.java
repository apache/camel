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
package org.apache.camel.cdi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.xml.bind.JAXBException;

import org.apache.camel.model.ModelHelper;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.util.ObjectHelper;

/**
 * A helper class for loading route definitions from a file, URL or the classpath
 */
public final class RoutesXml {
    
    private RoutesXml() {
        //The helper class
    }

    /**
     * Loads the routes from the given XML content
     */
    public static RoutesDefinition loadRoutesFromXML(String xml) throws JAXBException {
        return ModelHelper.createModelFromXml(xml, RoutesDefinition.class);
    }

    /**
     * Loads the routes from the classpath
     */
    public static RoutesDefinition loadRoutesFromClasspath(String uri) throws JAXBException {
        InputStream stream = ObjectHelper.loadResourceAsStream(uri);
        ObjectHelper.notNull(stream, "Could not find resource '" + uri + "' on the ClassLoader");
        return ModelHelper.createModelFromXml(stream, RoutesDefinition.class);
    }

    /**
     * Loads the routes from a {@link URL}
     */
    public static RoutesDefinition loadRoutesFromURL(URL url) throws JAXBException, IOException {
        ObjectHelper.notNull(url, "url");
        return ModelHelper.createModelFromXml(url.openStream(), RoutesDefinition.class);
    }

    /**
     * Loads the routes from a {@link URL}
     */
    public static RoutesDefinition loadRoutesFromURL(String url) throws IOException, JAXBException {
        return loadRoutesFromURL(new URL(url));
    }

    /**
     * Loads the routes from a {@link File}
     */
    public static RoutesDefinition loadRoutesFromFile(File file) throws JAXBException, FileNotFoundException {
        ObjectHelper.notNull(file, "file");
        return ModelHelper.createModelFromXml(new FileInputStream(file), RoutesDefinition.class);
    }

    /**
     * Loads the routes from a {@link File}
     */
    public static RoutesDefinition loadRoutesFromFile(String fileName)
        throws JAXBException, FileNotFoundException {
        return loadRoutesFromFile(new File(fileName));
    }

}
