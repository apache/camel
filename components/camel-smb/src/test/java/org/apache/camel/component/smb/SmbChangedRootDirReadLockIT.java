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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.io.ArrayByteChunkProvider;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmbChangedRootDirReadLockIT extends SmbServerTestSupport {

    @TempDir
    Path testDirectory;

    // reads only '*.dat' to exclude other files in the SMB home dir otherwise the test shuts down before
    // all the files are processed
    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/&readLock=changed&searchPattern=*.dat&readLockCheckInterval=1000&delete=true",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testChangedReadLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists(testDirectory.resolve("slowfile.dat"));

        writeSlowFile();

        MockEndpoint.assertIsSatisfied(context);

        String content = context.getTypeConverter().convertTo(String.class, testDirectory.resolve("slowfile.dat").toFile());
        String[] lines = content.split(System.lineSeparator());
        assertEquals(20, lines.length, "There should be 20 lines in the file");
        for (int i = 0; i < 20; i++) {
            assertEquals("Line " + i, lines[i]);
        }
    }

    private void writeSlowFile() throws Exception {

        SMBClient smbClient = new SMBClient();
        int port = Integer.parseInt(service.address().split(":")[1]);
        try (Connection connection = smbClient.connect("localhost", port)) {
            AuthenticationContext ac = new AuthenticationContext(service.userName(), service.password().toCharArray(), null);
            Session session = connection.authenticate(ac);

            // write file to SMB dir
            try (DiskShare share = (DiskShare) session.connectShare(service.shareName())) {
                try (File f = share.openFile("slowfile.dat", EnumSet.of(AccessMask.FILE_WRITE_DATA),
                        EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL), SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN_IF, EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE))) {

                    int offset = 0;
                    for (int i = 0; i < 20; i++) {
                        byte[] b = ("Line " + i + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                        f.write(new ArrayByteChunkProvider(b, offset));
                        offset += b.length;
                        Thread.sleep(200L);
                    }
                }
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getSmbUrl()).to(TestSupport.fileUri(testDirectory), "mock:result");
            }
        };
    }
}
