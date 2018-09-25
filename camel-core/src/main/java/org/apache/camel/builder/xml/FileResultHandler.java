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
package org.apache.camel.builder.xml;

import java.io.File;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;

import org.apache.camel.Message;

/**
 * Use file to handle the result of XSLT transformation.
 * <p/>
 * For example when transforming big files you can stream directly to a file output
 * to avoid consuming to much memory.
 *
 * @version 
 */
public class FileResultHandler implements ResultHandler {

    private final File file;
    private final StreamResult result;

    public FileResultHandler(File file) {
        this.file = file;
        this.result = new StreamResult(file);
    }

    public Result getResult() {
        return result;
    }

    public void setBody(Message in) {
        in.setBody(file);
    }

}
