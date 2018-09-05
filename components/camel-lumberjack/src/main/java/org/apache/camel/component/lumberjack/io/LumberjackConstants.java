/**
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
package org.apache.camel.component.lumberjack.io;

final class LumberjackConstants {
    static final int VERSION_V1 = '1';
    static final int VERSION_V2 = '2';

    static final int TYPE_ACKNOWLEDGE = 'A';

    static final int TYPE_WINDOW = 'W';
    static final int TYPE_COMPRESS = 'C';
    static final int TYPE_JSON = 'J';
    static final int TYPE_DATA = 'D';

    static final int INT_LENGTH = 4;

    static final int FRAME_HEADER_LENGTH = 1 + 1; // version(byte) + type(byte)

    static final int FRAME_ACKNOWLEDGE_LENGTH = FRAME_HEADER_LENGTH + INT_LENGTH; // sequence number(int)

    static final int FRAME_JSON_HEADER_LENGTH = INT_LENGTH + INT_LENGTH; // sequence number(int) + payload length(int)

    static final int FRAME_DATA_HEADER_LENGTH = INT_LENGTH + INT_LENGTH; // sequence number(int) + key/value pair count(int)

    static final int FRAME_WINDOW_HEADER_LENGTH = INT_LENGTH; // window size(int)

    static final int FRAME_COMPRESS_HEADER_LENGTH = INT_LENGTH; // compressed payload length(int)

    private LumberjackConstants() {
    }
}
