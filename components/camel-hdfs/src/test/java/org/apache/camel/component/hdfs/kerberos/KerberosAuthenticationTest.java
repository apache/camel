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
package org.apache.camel.component.hdfs.kerberos;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.junit.Test;

import static org.apache.camel.component.hdfs.HdfsTestSupport.CWD;

public class KerberosAuthenticationTest {

    private KerberosAuthentication underTest;

    @Test
    public void loginWithKeytabFile() throws IOException {
        // given
        Configuration configuration = new Configuration();

        String username = "test_user";
        String keyTabFileLocation = CWD.getAbsolutePath() + "/src/test/resources/kerberos/test-keytab.bin";

        underTest = new KerberosAuthentication(configuration, username, keyTabFileLocation);

        // when
        underTest.loginWithKeytab();

        // then
        /* message is printed in the logs */
    }

    @Test(expected = FileNotFoundException.class)
    public void loginWithMissingKeytabFile() throws IOException {
        // given
        Configuration configuration = new Configuration();

        String username = "test_user";
        String keyTabFileLocation = CWD.getAbsolutePath() + "/src/test/resources/kerberos/missing.bin";

        underTest = new KerberosAuthentication(configuration, username, keyTabFileLocation);

        // when
        underTest.loginWithKeytab();

        // then
        /* exception was thrown */
    }

}
