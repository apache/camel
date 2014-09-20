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
package org.apache.camel.component.mongodb.converters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONCallback;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.IOHelper;
import org.bson.BSONCallback;
import org.bson.BasicBSONDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public final class MongoDbBasicConverters {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasicConverters.class);

    // Jackson's ObjectMapper is thread-safe, so no need to create a pool nor synchronize access to it
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MongoDbBasicConverters() {
    }
    
    @Converter
    public static DBObject fromMapToDBObject(Map<?, ?> map) {
        return new BasicDBObject(map);
    }
    
    @Converter
    public static Map<String, Object> fromBasicDBObjectToMap(BasicDBObject basicDbObject) {
        return basicDbObject;
    }
    
    @Converter
    public static DBObject fromStringToDBObject(String s) {
        DBObject answer = null;
        try {
            answer = (DBObject) JSON.parse(s);
        } catch (Exception e) {
            LOG.warn("String -> DBObject conversion selected, but the following exception occurred. Returning null.", e);
        }
        
        return answer;
    }
   
    @Converter
    public static DBObject fromFileToDBObject(File f, Exchange exchange) throws FileNotFoundException {
        return fromInputStreamToDBObject(new FileInputStream(f), exchange);
    }
    
    @Converter
    public static DBObject fromInputStreamToDBObject(InputStream is, Exchange exchange) {
        DBObject answer = null;
        try {
            byte[] input = IOConverter.toBytes(is);
            
            if (isBson(input)) {
                BSONCallback callback = new JSONCallback();
                new BasicBSONDecoder().decode(input, callback);
                answer = (DBObject) callback.get();
            } else {
                answer = (DBObject) JSON.parse(IOConverter.toString(input, exchange));
            }
        } catch (Exception e) {
            LOG.warn("String -> DBObject conversion selected, but the following exception occurred. Returning null.", e);
        } finally {
            // we need to make sure to close the input stream
            IOHelper.close(is, "InputStream", LOG);
        }
        return answer;
    }
   
    /** 
     * If the input starts with any number of whitespace characters and then a '{' character, we
     * assume it is JSON rather than BSON. There are probably no useful BSON blobs that fit this pattern
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
    public static DBObject fromAnyObjectToDBObject(Object value) {
        BasicDBObject answer;
        try {
            Map<?, ?> m = OBJECT_MAPPER.convertValue(value, Map.class);
            answer = new BasicDBObject(m);
        } catch (Exception e) {
            LOG.warn("Conversion has fallen back to generic Object -> DBObject, but unable to convert type {}. Returning null.", 
                    value.getClass().getCanonicalName());
            return null;
        }
        return answer;
    }
    
}
