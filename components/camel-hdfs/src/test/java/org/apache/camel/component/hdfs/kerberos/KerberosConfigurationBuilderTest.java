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

import org.junit.Test;

import static org.apache.camel.component.hdfs.HdfsTestSupport.CWD;
import static org.junit.Assert.*;

public class KerberosConfigurationBuilderTest {

    @Test
    public void withKerberosConfiguration() {
        // given
        String kerberosConfigFileLocation = CWD.getAbsolutePath() + "/src/test/resources/kerberos/test-kerb5.conf";

        // when
        KerberosConfigurationBuilder.setKerberosConfigFile(kerberosConfigFileLocation);

        // then

    }

    @Test
    public void setKerberosConfigFileWithRealFile() {
        // given
        String kerb5FileName = "test-kerb5.conf";
        String kerberosConfigFileLocation = CWD.getAbsolutePath() + "/src/test/resources/kerberos/" + kerb5FileName;

        // when
        KerberosConfigurationBuilder.setKerberosConfigFile(kerberosConfigFileLocation);

        // then
        String actual = System.getProperty("java.security.krb5.conf");
        assertNotNull(actual);
        assertTrue(actual.endsWith(kerb5FileName));
    }

    @Test
    public void setKerberosConfigFileWithMissingFile() {
        // given
        String kerb5FileName = "missing-kerb5.conf";
        String kerberosConfigFileLocation = CWD.getAbsolutePath() + "/src/test/resources/kerberos/" + kerb5FileName;

        // when
        KerberosConfigurationBuilder.setKerberosConfigFile(kerberosConfigFileLocation);

        // then
        String actual = System.getProperty("java.security.krb5.conf");
        assertNull(actual);
    }

}
