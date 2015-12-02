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
package org.apache.camel.component.aws.kinesis;

import com.amazonaws.services.kinesis.model.Record;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.apache.camel.Converter;

@Converter
public class RecordStringConverter {

    @Converter
    public static String toString(Record record) {
        List<Byte> bytes = new ArrayList<>();
        ByteBuffer buf = record.getData().asReadOnlyBuffer();
        while (buf.hasRemaining()) {
            bytes.add(buf.get());
        }
        byte[] a = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); ++i) {
            a[i] = bytes.get(i);
        }
        return new String(a, Charset.forName("UTF-8"));
    }

}
