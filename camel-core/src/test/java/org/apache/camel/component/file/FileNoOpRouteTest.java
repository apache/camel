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

/**
 * @version $Revision: 1.1 $
 */
public class FileNoOpRouteTest extends FileRouteTest {
    @Override
    protected void setUp() throws Exception {
        uri = "file:target/test-noop-inbox?noop=true";

        // lets delete all the files
        File oldDir = new File("target/test-noop-inbox");
        if (oldDir.exists()) {
            File parentDir = oldDir.getParentFile();
            File[] files = parentDir.listFiles();
            File newName = new File(parentDir, oldDir.getName() + "-" + (files.length + 1));
            log.debug("renaming old output: " + oldDir + " to: " + newName);
            oldDir.renameTo(newName);
        }

        super.setUp();
    }
}
