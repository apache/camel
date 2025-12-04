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
 * Tests the default rename behavior using copy and delete strategy ({@code renameUsingCopy=true}). This test verifies
 * that:
 *
 * When {@code renameUsingCopy=true} (the default), files are moved using copy+delete strategy Atomic rename is NOT
 * attempted (verified by throwing an exception if called) Files are successfully moved using the reliable copy+delete
 * approach The copy+delete strategy is the default because it's more reliable across different scenarios: Works across
 * different file systems (e.g., moving from one network share to another) Handles cases where atomic rename is not
 * supported Provides better compatibility in heterogeneous network environments While atomic rename
 * ({@code renameUsingCopy=false}) is more efficient, copy+delete is safer and more predictable, especially in
 * distributed systems.
 *
 * @see SmbAtomicRenameBehaviorIT for the optimized atomic rename approach
 */
public class SmbRenameUsingCopyIT extends AbstractSmbRenameIT {

    private static final String PATH = "renameUsingCopy";
    private static final String FILENAME = "renameUsingCopyStrategy.txt";

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/%s?username=%s&password=%s&move=.done/${file:name}&renameUsingCopy=true",
                service.address(), service.shareName(), getPath(), service.userName(), service.password());
    }

    @Override
    protected SmbOperations createCustomSmbOperation(SmbConfiguration configuration) {
        return new CustomSmbOperations(configuration);
    }

    /**
     * Custom operations that verify copy+delete is being used by throwing an exception if atomic rename is invoked.
     */
    class CustomSmbOperations extends SmbOperations {
        public CustomSmbOperations(SmbConfiguration configuration) {
            super(configuration);
        }

        /**
         * Overrides atomic rename to throw an exception, verifying it's not called when using copy+delete.
         *
         * @throws RuntimeException always, to fail the test if this method is called
         */
        @Override
        public boolean atomicRenameFile(File src, String to) throws GenericFileOperationFailedException {
            throw new RuntimeException("This exception should not be thrown! Test Failed!");
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
