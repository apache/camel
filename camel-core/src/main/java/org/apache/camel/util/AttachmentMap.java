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
package org.apache.camel.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;

import org.apache.camel.Attachment;
import org.apache.camel.impl.DefaultAttachment;

/**
 * The AttachmentMap class provides a transparent Map<String, DataHandler>
 * interface for a Map<String, Attachment>
 */
public class AttachmentMap extends AbstractMap<String, DataHandler> {
    private Map<String, Attachment> map;

    public AttachmentMap(Map<String, Attachment> backingMap) {
        this.map = backingMap;
    }

    @Override
    public DataHandler put(String key, DataHandler value) {
        Attachment old = map.put(key, new DefaultAttachment(value));
        if (old == null) {
            return null;
        } else {
            return old.getDataHandler();
        }
    }

    @Override
    public Set<Map.Entry<String, DataHandler>> entrySet() {
        return new AttachmentEntrySet(map.entrySet());
    }

    public Map<String, Attachment> getOriginalMap() {
        return map;
    }

    private static class AttachmentEntrySet extends AbstractSet<Map.Entry<String, DataHandler>> {
        private Set<Map.Entry<String, Attachment>> set;

        AttachmentEntrySet(Set<Map.Entry<String, Attachment>> set) {
            this.set = set;
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        public Iterator<Map.Entry<String, DataHandler>> iterator() {
            return new AttachmentEntrySetIterator(set.iterator());
        }
    }

    private static class AttachmentEntrySetIterator implements Iterator<Map.Entry<String, DataHandler>> {
        private Iterator<Map.Entry<String, Attachment>> iter;

        AttachmentEntrySetIterator(Iterator<Map.Entry<String, Attachment>> origIterator) {
            iter = origIterator;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Map.Entry<String, DataHandler> next() {
            return new AttachmentEntry(iter.next());
        }

        public void remove() {
            iter.remove();
        }
    }

    private static class AttachmentEntry implements Map.Entry<String, DataHandler> {
        private Map.Entry<String, Attachment> entry;

        AttachmentEntry(Map.Entry<String, Attachment> backingEntry) {
            this.entry = backingEntry;
        }

        @Override
        public String getKey() {
            return entry.getKey();
        }

        @Override
        public DataHandler getValue() {
            Attachment value = entry.getValue();
            if (value != null) {
                return value.getDataHandler();
            }
            return null;
        }

        @Override
        public DataHandler setValue(DataHandler value) {
            Attachment oldValue = entry.setValue(new DefaultAttachment(value));
            if (oldValue != null) {
                return oldValue.getDataHandler();
            }
            return null;
        }

        // two AttachmentEntry objects are equal if the backing entries are equal
        public boolean equals(Object o) {
            if (o instanceof AttachmentEntry && entry.equals(((AttachmentEntry)o).entry)) {
                return true;
            }
            return false;
        }

        public int hashCode() {
            return entry.hashCode();
        }
    }
}
