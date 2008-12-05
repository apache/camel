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
import java.io.IOException;
import java.util.Date;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

/**
 * A {@link Exchange} for File
 *
 * @version $Revision$
 */
public class FileExchange extends DefaultExchange {
    private File file;

    public FileExchange(CamelContext camelContext, ExchangePattern pattern, File file) {
        super(camelContext, pattern);
        setFile(file);
    }

    public FileExchange(DefaultExchange parent, File file) {
        super(parent);
        setFile(file);
    }

    public File getFile() {
        return this.file;
    }

    public void setFile(File file) {
        setIn(new FileMessage(file));
        this.file = file;
        populateHeaders(file);
    }

    public Exchange newInstance() {
        return new FileExchange(this, getFile());
    }

    private void populateHeaders(File file) {
        // set additional headers with file information
        if (file != null) {
            getIn().setHeader("CamelFileName", file.getName());
            getIn().setHeader("CamelFileAbsolutePath", file.getAbsolutePath());
            getIn().setHeader("CamelFileParent", file.getParent());
            getIn().setHeader("CamelFilePath", file.getPath());
            try {
                getIn().setHeader("CamelFileCanonicalPath", file.getCanonicalPath());
            } catch (IOException e) {
                // ignore
            }
            if (file.length() > 0) {
                getIn().setHeader("CamelFileLength", new Long(file.length()));
            }
            if (file.lastModified() > 0) {
                getIn().setHeader("CamelFileLastModified", new Date(file.lastModified()));
            }
        }
    }

}
