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
package org.apache.camel.component.file;

import java.io.File;
import java.util.Map;

/**
 * File component.
 */
public class FileComponent extends GenericFileComponent<File> {

    /**
     * Default camel lock filename postfix
     */
    public static final String DEFAULT_LOCK_FILE_POSTFIX = ".camelLock";

    protected GenericFileEndpoint<File> buildFileEndpoint(String uri, String remaining, Map parameters) throws Exception {
        File file = new File(remaining);

        FileEndpoint result = new FileEndpoint(uri, this);
        result.setFile(file);

        GenericFileConfiguration config = new GenericFileConfiguration();
        config.setFile(file.getPath());
        result.setConfiguration(config);

        FileOperations operations = new FileOperations();
        operations.setEndpoint(result);
        result.setOperations(operations);

        return result;
    }

    protected void afterPropertiesSet(GenericFileEndpoint<File> endpoint) throws Exception {
        // noop
    }
}
