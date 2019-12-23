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
package org.apache.camel.component.dataset;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.util.IOHelper;
import org.apache.camel.util.Scanner;

/**
 * A DataSet that reads payloads from a file that are used to create each message exchange
 * along with using a pluggable transformer to customize the messages.  The file contents may optionally
 * be split using a supplied token.
 */
public class FileDataSet extends ListDataSet {
    private File sourceFile;
    private String delimiter;

    private List<Object> defaultBodies;

    public FileDataSet(String sourceFileName) throws IOException {
        this(new File(sourceFileName));
    }

    public FileDataSet(File sourceFile) throws IOException {
        this(sourceFile, null);
    }

    public FileDataSet(String sourceFileName, String delimiter) throws IOException {
        this(new File(sourceFileName), delimiter);
    }

    public FileDataSet(File sourceFile, String delimiter) throws IOException {
        setSourceFile(sourceFile, delimiter);
    }

    // Properties
    //-------------------------------------------------------------------------

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile) throws IOException {
        this.sourceFile = sourceFile;
        readSourceFile();
    }

    public void setSourceFile(File sourceFile, String delimiter) throws IOException {
        this.sourceFile = sourceFile;
        this.delimiter = delimiter;
        readSourceFile();
    }

    public String getDelimiter() {
        return delimiter;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    private void readSourceFile() throws IOException {
        List<Object> bodies = new LinkedList<>();
        if (delimiter != null) {
            try (Scanner scanner = new Scanner(sourceFile, null, delimiter)) {
                while (scanner.hasNext()) {
                    String nextPayload = scanner.next();
                    if ((nextPayload != null) && (nextPayload.length() > 0)) {
                        bodies.add(nextPayload);
                    }
                }
            }
        } else {
            Object data = IOHelper.loadText(new FileInputStream(sourceFile));
            bodies.add(data);
        }

        setDefaultBodies(bodies);
    }
}
