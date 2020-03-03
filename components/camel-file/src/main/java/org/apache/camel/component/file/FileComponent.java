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
import org.apache.camel.component.file.strategy.FileProcessStrategyFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;

/**
 * * The <a href="http://camel.apache.org/file.html">File Component</a> provides
 * access to file systems.
 */
@Component("file")
@FileProcessStrategy(FileProcessStrategyFactory.class)
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
    protected GenericFileEndpoint<File> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // the starting directory must be a static (not containing dynamic
        // expressions)
        if (StringHelper.hasStartToken(remaining, "simple")) {
            throw new IllegalArgumentException("Invalid directory: " + remaining + ". Dynamic expressions with ${ } placeholders is not allowed."
                                               + " Use the fileName option to set the dynamic expression.");
        }

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
