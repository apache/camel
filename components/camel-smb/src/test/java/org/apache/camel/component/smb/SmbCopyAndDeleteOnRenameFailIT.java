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

import com.hierynomus.smbj.share.File;
import org.apache.camel.component.file.GenericFileOperationFailedException;

/**
 * Tests the fallback behavior when {@code copyAndDeleteOnRenameFail=true} is enabled. This test verifies that: When
 * {@code copyAndDeleteOnRenameFail} is true, atomic rename is attempted first, If atomic rename fails (simulated by
 * returning false), the component falls back to copy+delete, Files are successfully moved even when atomic rename is
 * not supported This configuration is useful for environments where atomic rename might fail (e.g., moving files across
 * different file systems or network boundaries) but you still want the operation to succeed using copy+delete as a
 * fallback. The test simulates atomic rename failure by overriding {@code atomicRenameFile} to return false, forcing
 * the fallback to copy+delete strategy.
 *
 * @see SmbAtomicRenameBehaviorIT for atomic rename without fallback
 */
public class SmbCopyAndDeleteOnRenameFailIT extends AbstractSmbRenameIT {

    private static final String FILENAME = "copyAndDeleteOnRenameFailScenario.txt";
    private static final String PATH = "copyAndDeleteOnRenameFail";

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/%s?username=%s&password=%s&move=.done/${file:name}&copyAndDeleteOnRenameFail=true",
                service.address(), service.shareName(), getPath(), service.userName(), service.password());
    }

    @Override
    protected SmbOperations createCustomSmbOperation(SmbConfiguration configuration) {
        return new CustomSmbOperations(configuration);
    }

    /**
     * Custom operations that simulate atomic rename failure to test the fallback mechanism.
     */
    class CustomSmbOperations extends SmbOperations {
        public CustomSmbOperations(SmbConfiguration configuration) {
            super(configuration);
        }

        /**
         * Overrides atomic rename to always throw an exception, simulating a rename failure. This forces the component
         * to use the copy+delete fallback strategy.
         *
         * @throws GenericFileOperationFailedException always, to simulate atomic rename failure
         */
        @Override
        public boolean atomicRenameFile(File src, String to) throws GenericFileOperationFailedException {
            // Force failure to trigger fallback to copy+delete
            throw new GenericFileOperationFailedException("Simulated atomic rename failure for testing fallback");
        }
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
