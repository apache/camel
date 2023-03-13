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
package org.apache.camel.tooling.maven;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MavenTest {

    public static final Logger LOG = LoggerFactory.getLogger(MavenTest.class);

    @Test
    public void masterPasswords() throws Exception {
        PlexusCipher pc = new DefaultPlexusCipher();
        String mp = pc.encrypt("camel", DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);
        LOG.info("master password: {}", mp); // e.g., "SHrYKy0oCBEH5SHhRcuv0U52J3E908O23QWHDyEiGtQ="
        String p = pc.encrypt("passw0rd", "camel");
        LOG.info("password: {}", p); // e.g., "9V4tKIxO4ZsHx63bkn9uy6zsYM9VJyG03sTsPzPDK9c="

        assertEquals("passw0rd", pc.decrypt(p, pc.decrypt(mp, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION)));
        assertEquals("camel", pc.decrypt(mp, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION));
    }

}
