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
package org.apache.camel.maven.connector;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;

import java.io.IOException;
import java.util.HashMap;

/**
 * Camel component format is tricky.  properties and component properties need to be encodes 1 to line.  We
 * use a small hack of using a HashMap to identify those values and print those using a MinimalPrettyPrinter.
 */
class CamelComponentPrettyPrinter implements PrettyPrinter {
    DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
    MinimalPrettyPrinter mpp = new MinimalPrettyPrinter();

    private boolean useSimpleFormat(JsonGenerator gen) {
        return gen.getCurrentValue()!=null && gen.getCurrentValue().getClass() == HashMap.class;
    }

    @Override
    public void writeRootValueSeparator(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            pp.writeRootValueSeparator(gen);
        } else {
            pp.writeRootValueSeparator(gen);
        }
    }

    @Override
    public void writeStartObject(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.writeStartObject(gen);
        } else {
            pp.writeStartObject(gen);
        }
    }

    @Override
    public void writeEndObject(JsonGenerator gen, int nrOfEntries) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.writeEndObject(gen, nrOfEntries);
        } else {
            pp.writeEndObject(gen, nrOfEntries);
        }
    }

    @Override
    public void writeObjectEntrySeparator(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.writeObjectEntrySeparator(gen);
        } else {
            pp.writeObjectEntrySeparator(gen);
        }
    }

    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.writeObjectFieldValueSeparator(gen);
        } else {
            pp.writeObjectFieldValueSeparator(gen);
        }
    }

    @Override
    public void writeStartArray(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.writeStartArray(gen);
        } else {
            pp.writeStartArray(gen);
        }
    }

    @Override
    public void writeEndArray(JsonGenerator gen, int nrOfValues) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.writeEndArray(gen, nrOfValues);
        } else {
            pp.writeEndArray(gen, nrOfValues);
        }
    }

    @Override
    public void writeArrayValueSeparator(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.writeArrayValueSeparator(gen);
        } else {
            pp.writeArrayValueSeparator(gen);
        }
    }

    @Override
    public void beforeArrayValues(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.beforeArrayValues(gen);
        } else {
            pp.beforeArrayValues(gen);
        }
    }

    @Override
    public void beforeObjectEntries(JsonGenerator gen) throws IOException, JsonGenerationException {
        if (useSimpleFormat(gen)) {
            mpp.beforeObjectEntries(gen);
        } else {
            pp.beforeObjectEntries(gen);
        }
    }
}
