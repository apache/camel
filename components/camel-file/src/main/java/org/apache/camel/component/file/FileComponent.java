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
package org.apache.camel.component.file;

import java.io.File;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

/**
 * * The <a href="http://camel.apache.org/file.html">File Component</a> provides access to file systems.
 */
@Component("file")
public class FileComponent extends GenericFileComponent<File> {
    /**
     * GenericFile property on Camel Exchanges.
     */
    public static final String FILE_EXCHANGE_FILE = "CamelFileExchangeFile";

    /**
     * Default camel lock filename postfix
     */
    public static final String DEFAULT_LOCK_FILE_POSTFIX = ".camelLock";

    public FileComponent() {
    }

    public FileComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<File> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        // the starting directory must be a static (not containing dynamic
        // expressions)
        if (StringHelper.hasStartToken(remaining, "simple")) {
            throw new IllegalArgumentException(
                    "Invalid directory: " + remaining + ". Dynamic expressions with ${ } placeholders is not allowed."
                                               + " Use the fileName option to set the dynamic expression."
                                               + " The directory is fixed when the endpoint is created, so from, to and"
                                               + " pollEnrich with a fixed uri do not evaluate it: use toD to write to a"
                                               + " dynamic directory, or pollEnrich with a simple expression"
                                               + " (pollEnrich().simple(\"...\") in Java) to read from one.");
        }
        if (remaining.startsWith("classpath:") || remaining.startsWith("resource:")) {
            // file:classpath:data is the directory classpath:data of the file system, which the consumer creates
            String resource = remaining.startsWith("resource:") ? remaining : "resource:" + remaining;
            if (!resource.endsWith(":") && !resource.endsWith("/")) {
                resource += "/";
            }
            log.warn("The directory {} of {} is a directory of the file system, not of the classpath: the file component"
                     + " does not read classpath resources. To read a classpath resource use the language component,"
                     + " such as language:constant:{}<file name>",
                    remaining, URISupport.sanitizeUri(uri), resource);
        }

        remaining = FileUtil.normalizePath(remaining);

        File file = new File(remaining);

        FileEndpoint result = new FileEndpoint(uri, this);
        result.setFile(file);

        GenericFileConfiguration config = new GenericFileConfiguration();
        config.setDirectory(FileUtil.isAbsolute(file) ? file.getAbsolutePath() : file.getPath());
        result.setConfiguration(config);

        return result;
    }

    @Override
    protected void afterPropertiesSet(GenericFileEndpoint<File> endpoint) throws Exception {
        // noop
    }

}
