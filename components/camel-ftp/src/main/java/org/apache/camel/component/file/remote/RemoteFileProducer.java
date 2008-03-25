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
package org.apache.camel.component.file.remote;

import org.apache.camel.Message;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.impl.DefaultProducer;

public abstract class RemoteFileProducer<T extends RemoteFileExchange> extends DefaultProducer<T> {

    protected RemoteFileProducer(RemoteFileEndpoint<T> endpoint) {
        super(endpoint);
    }

    protected String createFileName(Message message, RemoteFileConfiguration fileConfig) {
        String answer;
        String endpointFileName = fileConfig.getFile();
        String headerFileName = message.getHeader(FileComponent.HEADER_FILE_NAME, String.class);
        if (fileConfig.isDirectory()) {
            // If the path isn't empty, we need to add a trailing / if it isn't already there
            String baseDir = "";
            if (endpointFileName.length() > 0) {
                baseDir = endpointFileName + (endpointFileName.endsWith("/") ? "" : "/");
            }
            String fileName = (headerFileName != null) ? headerFileName : message.getMessageId();
            answer = baseDir + fileName;
        } else {
            answer = endpointFileName;
        }
        return answer;
    }

    protected abstract void connectIfNecessary() throws Exception;

    protected abstract void disconnect() throws Exception;
}
