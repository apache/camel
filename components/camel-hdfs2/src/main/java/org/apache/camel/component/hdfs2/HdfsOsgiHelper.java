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
package org.apache.camel.component.hdfs2;

import java.net.URI;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Class which may be used in OSGi/Blueprint environment to perform some static initialization</p>
 * <p>This could be useful to fix the usage of {@link ServiceLoader} by Hadoop 2 in OSGi environment.</p>
 */
public class HdfsOsgiHelper {

    private static final Logger LOG = LoggerFactory.getLogger(HdfsOsgiHelper.class);

    /**
     * By using this constructor it is possible to perform static initialization of {@link FileSystem}.
     */
    public HdfsOsgiHelper(Map<String, String> fileSystems) {
        try {
            // get bundle classloader for camel-hdfs2 bundle
            ClassLoader cl = getClass().getClassLoader();
            Configuration conf = new Configuration();
            // set that as the hdfs configuration's classloader
            conf.setClassLoader(cl);
            for (String key : fileSystems.keySet()) {
                URI uri = URI.create(key);
                conf.setClass(String.format("fs.%s.impl", uri.getScheme()), cl.loadClass(fileSystems.get(key)), FileSystem.class);
                LOG.debug("Successfully loaded class: {}", fileSystems.get(key));
                FileSystem.get(uri, conf);
                LOG.debug("Successfully got uri: {} from FileSystem Object", uri);
            }
        } catch (Exception e) {
            LOG.debug(e.getMessage(), e);
        }
    }

}
