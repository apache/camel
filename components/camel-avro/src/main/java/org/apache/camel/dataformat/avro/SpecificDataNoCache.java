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
package org.apache.camel.dataformat.avro;

import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.specific.SpecificData;

public class SpecificDataNoCache extends SpecificData {

    public SpecificDataNoCache() {
        super();
        addLogicalTypeConversions();
    }

    public SpecificDataNoCache(ClassLoader classLoader) {
        super(classLoader);
        addLogicalTypeConversions();
    }

    private void addLogicalTypeConversions() {
        addLogicalTypeConversion(new TimeConversions.DateConversion());
        addLogicalTypeConversion(new TimeConversions.TimeMillisConversion());
        addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion());
        addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
        addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion());
        addLogicalTypeConversion(new TimeConversions.TimestampNanosConversion());
        addLogicalTypeConversion(new TimeConversions.LocalTimestampMillisConversion());
        addLogicalTypeConversion(new TimeConversions.LocalTimestampMicrosConversion());
        addLogicalTypeConversion(new TimeConversions.LocalTimestampNanosConversion());

        addLogicalTypeConversion(new org.apache.avro.Conversions.UUIDConversion());

        addLogicalTypeConversion(new org.apache.avro.Conversions.DecimalConversion());
    }

    @Override
    public Object newRecord(Object old, Schema schema) {
        Class<?> c = this.getClass(schema);
        return c == null ? super.newRecord(old, schema) : (c.isInstance(old) ? old : newInstance(c, schema));
    }

}
