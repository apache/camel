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
package org.apache.camel.component.dataset;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * A DataSet that reads payloads from a file that are used to create each message exchange
 * along with using a pluggable transformer to customize the messages.  The file contents may optionally
 * be split using a supplied token.
 *
 * @version
 */
public class FileDataSet extends ListDataSet {
    private File sourceFile;
    private String delimiter = "\\z";

    private List<Object> defaultBodies;

    public FileDataSet(String sourceFileName) throws IOException {
        this(new File(sourceFileName));
    }

    public FileDataSet(File sourceFile) throws IOException {
        this(sourceFile, "\\z");
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
        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            Scanner scanner = new Scanner(br);
            scanner.useDelimiter(delimiter);
            while (scanner.hasNext()) {
                String nextPayload = scanner.next();
                if ((nextPayload != null)  &&  (nextPayload.length() > 0)) {
                    bodies.add(nextPayload);
                }
            }
            setDefaultBodies(bodies);
        }
    }
}
