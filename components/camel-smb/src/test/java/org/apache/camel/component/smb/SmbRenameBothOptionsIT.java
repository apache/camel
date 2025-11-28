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
package org.apache.camel.component.smb;

/**
 * Tests the precedence of configuration options when both {@code renameUsingCopy} and {@code copyAndDeleteOnRenameFail}
 * are enabled. This test verifies that:
 *
 * When both {@code renameUsingCopy=true} and {@code copyAndDeleteOnRenameFail=true} are set, {@code renameUsingCopy}
 * takes precedence The component uses copy+delete strategy immediately without attempting atomic rename No atomic
 * rename attempt is made (verified by throwing an exception if atomic rename is called) This behavior makes sense
 * because {@code renameUsingCopy} is an explicit directive to always use copy+delete, while
 * {@code copyAndDeleteOnRenameFail} is a conditional fallback. When both are true, the explicit directive wins.
 *
 * @see SmbRenameUsingCopyIT for the base behavior this test extends
 * @see SmbCopyAndDeleteOnRenameFailIT for fallback-only behavior
 */
public class SmbRenameBothOptionsIT extends SmbRenameUsingCopyIT {

    private static final String FILENAME = "renameBothOptionsOnScenario.txt";
    private static final String PATH = "renameBothOptionsOn";

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/%s?username=%s&password=%s&move=.done/${file:name}&renameUsingCopy=true&copyAndDeleteOnRenameFail=true",
                service.address(), service.shareName(), getPath(), service.userName(), service.password());
    }

    @Override
    protected String getFilename() {
        return FILENAME;
    }

    @Override
    protected String getPath() {
        return PATH;
    }

}
