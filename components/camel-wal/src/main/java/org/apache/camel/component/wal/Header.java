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

package org.apache.camel.component.wal;

/**
 * This container represents a header of a transaction log file
 */
public final class Header {
    public static final int FORMAT_NAME_SIZE = 8;
    public static final String FORMAT_NAME = "camel-wa";
    public static final int CURRENT_FILE_VERSION = 1;
    public static final Header WA_DEFAULT_V1;
    public static final int BYTES;
    private final String formatName;
    private final int fileVersion;

    static {
        WA_DEFAULT_V1 = new Header(FORMAT_NAME, CURRENT_FILE_VERSION);

        BYTES = FORMAT_NAME_SIZE + Integer.BYTES;
    }

    Header(final String formatName, int fileVersion) {
        if (formatName == null || formatName.isEmpty()) {
            throw new IllegalArgumentException("The format name is not valid: it's either empty or null");
        }

        if (formatName.length() > FORMAT_NAME_SIZE) {
            throw new IllegalArgumentException(
                    "The format name '" + formatName + "' is too short. Its length must be less than " + FORMAT_NAME_SIZE);
        }

        this.formatName = formatName;
        this.fileVersion = fileVersion;
    }

    public String getFormatName() {
        return formatName;
    }

    public int getFileVersion() {
        return fileVersion;
    }
}
