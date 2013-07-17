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
package org.apache.camel.impl;

import java.io.File;

import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultStreamCachingStrategy extends org.apache.camel.support.ServiceSupport implements StreamCachingStrategy {

    // TODO: Add options to configure more stuff like overflow size et all
    // TODO: Add JMX management
    // TODO: Maybe use #syntax# for default temp dir so ppl can easily configure this

    private static final Logger LOG = LoggerFactory.getLogger(DefaultStreamCachingStrategy.class);
    private File temporaryDirectory;

    public void setTemporaryDirectory(File path) {
        this.temporaryDirectory = path;
    }

    public File getTemporaryDirectory() {
        return temporaryDirectory;
    }

    @Override
    protected void doStart() throws Exception {
        // create random temporary directory if none has been created
        if (temporaryDirectory == null) {
            temporaryDirectory = FileUtil.createNewTempDir();
            LOG.info("Created temporary directory {}", temporaryDirectory);
        } else {
            if (!temporaryDirectory.exists()) {
                boolean created = temporaryDirectory.mkdirs();
                if (!created) {
                    LOG.warn("Cannot create temporary directory {}", temporaryDirectory);
                } else {
                    LOG.info("Created temporary directory {}", temporaryDirectory);
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (temporaryDirectory != null) {
            LOG.info("Removing temporary directory {}", temporaryDirectory);
            FileUtil.removeDir(temporaryDirectory);
        }
    }
}
