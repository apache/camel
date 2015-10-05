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
package org.apache.camel.converter.dozer;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.dozer.util.DozerClassLoader;
import org.dozer.util.MappingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DozerThreadContextClassLoader implements DozerClassLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DozerThreadContextClassLoader.class);

    @Override
    public Class<?> loadClass(String className) {
        LOG.debug("Loading class from classloader: {}.", Thread.currentThread().getContextClassLoader());
        Class<?> result = null;
        try {
            // try to resolve the class from the thread context classloader
            result = ClassUtils.getClass(Thread.currentThread().getContextClassLoader(), className);
        } catch (ClassNotFoundException e) {
            MappingUtils.throwMappingException(e);
        }
        return result;
    }

    @Override
    public URL loadResource(String uri) {
        URL answer = null;

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            LOG.debug("Loading resource from classloader: {}.", cl);
            answer = cl.getResource(uri);
        }

        // try treating it as a system resource
        if (answer == null) {
            answer = ClassLoader.getSystemResource(uri);
        }

        // one more time in case it's a normal URI
        if (answer == null && StringUtils.contains(uri, ":")) {
            try {
                answer = new URL(uri);
            } catch (MalformedURLException e) {
                MappingUtils.throwMappingException(e);
            }
        }

        return answer;
    }

}
