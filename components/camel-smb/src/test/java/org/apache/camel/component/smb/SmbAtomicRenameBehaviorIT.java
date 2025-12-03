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

import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.share.File;

/**
 * Tests atomic rename behavior when {@code renameUsingCopy=false} is explicitly set. This test verifies that: When
 * {@code renameUsingCopy} is disabled, the component uses atomic rename operations The copy+delete strategy is NOT used
 * (verified by throwing an exception if called) Files are successfully moved using the optimized atomic rename approach
 *
 * @see SmbRenameUsingCopyIT for the default behavior
 */
public class SmbAtomicRenameBehaviorIT extends AbstractSmbRenameIT {

    private static final String PATH = "atomicRename";
    private static final String FILENAME = "atomicRenameScenario.txt";

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s/%s?username=%s&password=%s&move=.done/${file:name}&copyAndDeleteOnRenameFail=false",
                service.address(), service.shareName(), getPath(), service.userName(), service.password());
    }

    @Override
    protected SmbOperations createCustomSmbOperation(SmbConfiguration configuration) {
        return new CustomSmbOperations(configuration);
    }

    /**
     * Custom operations that verify atomic rename is being used by throwing an exception if the copy+delete strategy is
     * invoked.
     */
    class CustomSmbOperations extends SmbOperations {
        public CustomSmbOperations(SmbConfiguration configuration) {
            super(configuration);
        }

        /**
         * Overrides copy+delete to throw an exception, verifying it's not called when using atomic rename.
         *
         * @throws RuntimeException always, to fail the test if this method is called
         */
        public boolean copyAndDeleteRenameStrategy(File src, String to) throws SMBApiException {
            throw new RuntimeException("this should not have been thrown");
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
