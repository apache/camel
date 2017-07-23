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
package org.apache.camel.component.mongodb3.converters;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.IOHelper;
import org.bson.BsonArray;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.BsonArrayCodec;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.json.JsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public final class MongoDbBasicConverters {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasicConverters.class);

    private MongoDbBasicConverters() {
    }

    @Converter
    public static Document fromMapToDocument(Map<String, Object> map) {
        return new Document(map);
    }

    @Converter
    public static Map<String, Object> fromDocumentToMap(Document document) {
        return document;
    }

    @Converter
    public static Document fromStringToDocument(String s) {
        return Document.parse(s);
    }

    @Converter
    public static Document fromFileToDocument(File f, Exchange exchange) throws Exception {
        return fromInputStreamToDocument(new FileInputStream(f), exchange);
    }

    @Converter
    public static Document fromInputStreamToDocument(InputStream is, Exchange exchange) throws Exception {
        Document answer = null;
        try {
            byte[] input = IOConverter.toBytes(is);

            if (isBson(input)) {
                JsonReader reader = new JsonReader(new String(input));
                DocumentCodec documentReader = new DocumentCodec();

                answer = documentReader.decode(reader, DecoderContext.builder().build());
            } else {
                answer = Document.parse(IOConverter.toString(input, exchange));
            }
        } finally {
            // we need to make sure to close the input stream
            IOHelper.close(is, "InputStream", LOG);
        }
        return answer;
    }

    /**
     * If the input starts with any number of whitespace characters and then a
     * '{' character, we assume it is JSON rather than BSON. There are probably
     * no useful BSON blobs that fit this pattern
     */
    private static boolean isBson(byte[] input) {
        int i = 0;
        while (i < input.length) {
            if (input[i] == '{') {
                return false;
            } else if (!Character.isWhitespace(input[i])) {
                return true;
            }
        }
        return true;
    }

    @Converter
    public static List<Bson> fromStringToList(String value) {

        final CodecRegistry codecRegistry = CodecRegistries.fromProviders(Arrays.asList(new ValueCodecProvider(), new BsonValueCodecProvider(), new DocumentCodecProvider()));

        JsonReader reader = new JsonReader(value);
        BsonArrayCodec arrayReader = new BsonArrayCodec(codecRegistry);

        BsonArray docArray = arrayReader.decode(reader, DecoderContext.builder().build());

        List<Bson> answer = new ArrayList<>(docArray.size());

        for (BsonValue doc : docArray) {
            answer.add(doc.asDocument());
        }
        return answer;
    }

}
