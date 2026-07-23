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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.hierynomus.smbj.share.File;

/**
 * An InputStream wrapper that closes the underlying smbj {@link File} handle when the stream is closed. smbj's
 * FileInputStream.close() does not close the File handle, so without this wrapper the remote SMB file handle leaks.
 */
class SmbFileClosingInputStream extends FilterInputStream {

    private final File smbFile;

    SmbFileClosingInputStream(InputStream delegate, File smbFile) {
        super(delegate);
        this.smbFile = smbFile;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            smbFile.close();
        }
    }
}
