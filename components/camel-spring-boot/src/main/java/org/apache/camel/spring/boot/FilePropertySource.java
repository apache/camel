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
package org.apache.camel.spring.boot;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

/**
 * To load properties from files, such as a secret mounted to the container.
 */
public class FilePropertySource extends PropertySource {

    private static final Logger LOG = LoggerFactory.getLogger(FilePropertySource.class);

    // properties for all the loaded files
    private final Properties properties;

    public FilePropertySource(String name, ApplicationContext applicationContext, String directory) {
        super(name);
        StringHelper.notEmpty(directory, "directory");

        Properties loaded = new Properties();
        try {
            Resource[] files = applicationContext.getResources(directory);
            for (Resource file : files) {
                if (file.exists()) {
                    try (FileInputStream fis = new FileInputStream(file.getFile())) {
                        LOG.debug("Loading properties from file: {}", file);
                        Properties extra = new Properties();
                        extra.load(fis);
                        if (!extra.isEmpty()) {
                            loaded.putAll(extra);
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }

        // if we loaded any files then store as properties
        if (loaded.isEmpty()) {
            properties = null;
            LOG.warn("No properties found while loading from: {}", directory);
        } else {
            properties = loaded;
            LOG.info("Loaded {} properties from: {}", properties.size(), directory);
        }
    }

    @Override
    public Object getProperty(String name) {
        Object answer = properties != null ? properties.getProperty(name) : null;
        LOG.trace("getProperty {} -> {}", name, answer);
        return answer;
    }
}
