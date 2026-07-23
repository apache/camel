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
package org.apache.camel.component.mongodb;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.ObjectHelper;
import org.bson.BsonDocument;

@JdkService("mongodb-adapter-factory")
public class MongoDbResumeAdapter implements ResumeAdapter, Cacheable {
    private ResumeCache<String> resumeCache;
    private String resumeTokenKey;
    private MongoDbChangeStreamsConsumer consumer;

    @Override
    public void resume() {
        if (resumeCache == null || ObjectHelper.isEmpty(resumeTokenKey) || consumer == null) {
            return;
        }

        String serializedToken = resumeCache.get(resumeTokenKey, String.class);
        if (ObjectHelper.isNotEmpty(serializedToken)) {
            consumer.setStartupResumeToken(BsonDocument.parse(serializedToken));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setCache(ResumeCache<?> cache) {
        this.resumeCache = (ResumeCache<String>) cache;
    }

    @Override
    public ResumeCache<?> getCache() {
        return resumeCache;
    }

    @Override
    public boolean add(OffsetKey<?> key, Offset<?> offset) {
        if (resumeCache == null) {
            return false;
        }

        String keyValue = String.valueOf(key.getValue());
        String offsetValue = offset.getValue(String.class);
        if (ObjectHelper.isNotEmpty(keyValue) && ObjectHelper.isNotEmpty(offsetValue)) {
            resumeCache.add(keyValue, offsetValue);
            return true;
        }

        return false;
    }

    public void setResumeTokenKey(String resumeTokenKey) {
        this.resumeTokenKey = resumeTokenKey;
    }

    public String getResumeTokenKey() {
        return resumeTokenKey;
    }

    public void setConsumer(MongoDbChangeStreamsConsumer consumer) {
        this.consumer = consumer;
    }
}
