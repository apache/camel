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
package org.apache.camel.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * A map that uses case insensitive keys, but preserves the original key cases.
 * <p/>
 * The map uses a custom hash table with case-insensitive hashing and comparison, providing O(1) for {@code get},
 * {@code put}, {@code containsKey} and {@code remove} operations without allocating temporary strings. Entries are
 * stored in insertion order.
 * <p/>
 * This map is <b>not</b> designed to be thread safe as concurrent access to it is not supposed to be performed by the
 * Camel routing engine.
 */
public class CaseInsensitiveMap extends AbstractMap<String, Object> implements Serializable {

    private static final @Serial long serialVersionUID = -8538318195477618308L;
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private static final int EMPTY = -1;

    // Static lookup table for deduplicating well-known header keys (e.g. Exchange constants).
    // Registered once at startup; read-only after that. Zero-allocation lookups.
    private static volatile int[] knownTable;
    private static volatile String[] knownEntries;
    private static volatile int[] knownChainNext;
    private static volatile int knownMask;

    /**
     * Registers a set of well-known header key strings for deduplication. When a key passed to {@link #put} matches one
     * of these strings (case-insensitive), the canonical reference from this set is stored instead of the caller's
     * string, reducing memory when many map instances carry the same headers (e.g. deserialized exchanges).
     * <p/>
     * This method is intended to be called once during framework startup.
     */
    public static void registerKnownKeys(Collection<String> keys) {
        int sz = keys.size();
        int tableSize = tableSizeFor(Math.max((int) (sz / LOAD_FACTOR) + 1, DEFAULT_CAPACITY));
        int mask = tableSize - 1;
        int[] tbl = new int[tableSize];
        Arrays.fill(tbl, EMPTY);
        String[] entries = keys.toArray(new String[0]);
        int[] chain = new int[entries.length];

        for (int i = 0; i < entries.length; i++) {
            int b = caseInsensitiveHash(entries[i]) & mask;
            chain[i] = tbl[b];
            tbl[b] = i;
        }

        knownEntries = entries;
        knownChainNext = chain;
        knownMask = mask;
        // assign table last — readers check knownTable != null as the gate
        knownTable = tbl;
    }

    private static String deduplicateKey(String key, int hash) {
        int[] tbl = knownTable;
        if (tbl == null) {
            return key;
        }
        int idx = tbl[hash & knownMask];
        while (idx != EMPTY) {
            if (knownEntries[idx].equalsIgnoreCase(key)) {
                return knownEntries[idx];
            }
            idx = knownChainNext[idx];
        }
        return key;
    }

    private transient int[] table;
    private transient String[] keys;
    private transient Object[] values;
    private transient int[] chainNext;

    private transient int size;
    private transient int usedSlots;
    private transient int threshold;

    public CaseInsensitiveMap() {
        init(DEFAULT_CAPACITY);
    }

    public CaseInsensitiveMap(Map<? extends String, ?> map) {
        init(tableSizeFor(Math.max((int) (map.size() / LOAD_FACTOR) + 1, DEFAULT_CAPACITY)));
        putAll(map);
    }

    private void init(int tableCapacity) {
        table = new int[tableCapacity];
        Arrays.fill(table, EMPTY);
        int entryCapacity = (int) (tableCapacity * LOAD_FACTOR) + 1;
        keys = new String[entryCapacity];
        values = new Object[entryCapacity];
        chainNext = new int[entryCapacity];
        size = 0;
        usedSlots = 0;
        threshold = (int) (tableCapacity * LOAD_FACTOR);
    }

    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return Math.max(DEFAULT_CAPACITY, n + 1);
    }

    static int caseInsensitiveHash(String key) {
        int h = 0;
        for (int i = 0, len = key.length(); i < len; i++) {
            char c = key.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32; // fast ASCII upper-to-lower
            } else if (c >= 128) {
                // full Unicode two-step fold for non-ASCII
                c = Character.toLowerCase(Character.toUpperCase(c));
            }
            h = 31 * h + c;
        }
        return h ^ (h >>> 16);
    }

    private int findIndex(String key) {
        int idx = table[caseInsensitiveHash(key) & (table.length - 1)];
        while (idx != EMPTY) {
            if (keys[idx].equalsIgnoreCase(key)) {
                return idx;
            }
            idx = chainNext[idx];
        }
        return EMPTY;
    }

    private int findIndex(String key, int hash) {
        int idx = table[hash & (table.length - 1)];
        while (idx != EMPTY) {
            if (keys[idx].equalsIgnoreCase(key)) {
                return idx;
            }
            idx = chainNext[idx];
        }
        return EMPTY;
    }

    @Override
    public Object get(Object key) {
        int idx = findIndex((String) key);
        return idx != EMPTY ? values[idx] : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return findIndex((String) key) != EMPTY;
    }

    @Override
    public boolean containsValue(Object value) {
        for (int i = 0; i < usedSlots; i++) {
            if (keys[i] != null && Objects.equals(value, values[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object put(String key, Object value) {
        int hash = caseInsensitiveHash(key);
        key = deduplicateKey(key, hash);
        int idx = findIndex(key, hash);
        if (idx != EMPTY) {
            Object old = values[idx];
            values[idx] = value;
            return old;
        }
        if (size >= threshold) {
            resize(table.length * 2);
            // table length changed, but hash is still valid
        }
        if (usedSlots >= keys.length) {
            int newCap = keys.length + (keys.length >> 1);
            keys = Arrays.copyOf(keys, newCap);
            values = Arrays.copyOf(values, newCap);
            chainNext = Arrays.copyOf(chainNext, newCap);
        }
        int slot = usedSlots++;
        keys[slot] = key;
        values[slot] = value;
        int b = hash & (table.length - 1);
        chainNext[slot] = table[b];
        table[b] = slot;
        size++;
        return null;
    }

    @Override
    public Object remove(Object key) {
        int hash = caseInsensitiveHash((String) key);
        int b = hash & (table.length - 1);
        int prev = EMPTY;
        int cur = table[b];
        while (cur != EMPTY) {
            if (keys[cur].equalsIgnoreCase((String) key)) {
                Object old = values[cur];
                if (prev == EMPTY) {
                    table[b] = chainNext[cur];
                } else {
                    chainNext[prev] = chainNext[cur];
                }
                keys[cur] = null;
                values[cur] = null;
                size--;
                return old;
            }
            prev = cur;
            cur = chainNext[cur];
        }
        return null;
    }

    private void removeByIndex(int idx) {
        String key = keys[idx];
        int b = caseInsensitiveHash(key) & (table.length - 1);
        int prev = EMPTY;
        int cur = table[b];
        while (cur != EMPTY) {
            if (cur == idx) {
                if (prev == EMPTY) {
                    table[b] = chainNext[idx];
                } else {
                    chainNext[prev] = chainNext[idx];
                }
                break;
            }
            prev = cur;
            cur = chainNext[cur];
        }
        keys[idx] = null;
        values[idx] = null;
        size--;
    }

    private void resize(int newTableCapacity) {
        int[] newTable = new int[newTableCapacity];
        Arrays.fill(newTable, EMPTY);
        int entryCap = Math.max((int) (newTableCapacity * LOAD_FACTOR) + 1, size + 1);
        String[] newKeys = new String[entryCap];
        Object[] newValues = new Object[entryCap];
        int[] newChainNext = new int[entryCap];

        int newSlot = 0;
        for (int i = 0; i < usedSlots; i++) {
            if (keys[i] != null) {
                newKeys[newSlot] = keys[i];
                newValues[newSlot] = values[i];
                int b = caseInsensitiveHash(keys[i]) & (newTableCapacity - 1);
                newChainNext[newSlot] = newTable[b];
                newTable[b] = newSlot;
                newSlot++;
            }
        }

        table = newTable;
        keys = newKeys;
        values = newValues;
        chainNext = newChainNext;
        usedSlots = newSlot;
        threshold = (int) (newTableCapacity * LOAD_FACTOR);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        Arrays.fill(table, EMPTY);
        Arrays.fill(keys, 0, usedSlots, null);
        Arrays.fill(values, 0, usedSlots, null);
        size = 0;
        usedSlots = 0;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        for (Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return new EntrySet();
    }

    private final class EntrySet extends AbstractSet<Entry<String, Object>> {
        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?, ?> e)) {
                return false;
            }
            int idx = findIndex((String) e.getKey());
            return idx != EMPTY && Objects.equals(values[idx], e.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry<?, ?> e)) {
                return false;
            }
            int idx = findIndex((String) e.getKey());
            if (idx != EMPTY && Objects.equals(values[idx], e.getValue())) {
                removeByIndex(idx);
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            CaseInsensitiveMap.this.clear();
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return new EntryIterator();
        }
    }

    private final class EntryIterator implements Iterator<Entry<String, Object>> {
        private int cursor;
        private int lastReturned = EMPTY;

        EntryIterator() {
            cursor = advance(0);
        }

        private int advance(int from) {
            for (int i = from; i < usedSlots; i++) {
                if (keys[i] != null) {
                    return i;
                }
            }
            return EMPTY;
        }

        @Override
        public boolean hasNext() {
            return cursor != EMPTY;
        }

        @Override
        public Entry<String, Object> next() {
            if (cursor == EMPTY) {
                throw new NoSuchElementException();
            }
            lastReturned = cursor;
            cursor = advance(cursor + 1);
            return new MapEntry(lastReturned);
        }

        @Override
        public void remove() {
            if (lastReturned == EMPTY) {
                throw new IllegalStateException();
            }
            removeByIndex(lastReturned);
            lastReturned = EMPTY;
        }
    }

    private final class MapEntry implements Entry<String, Object> {
        private final int index;

        MapEntry(int index) {
            this.index = index;
        }

        @Override
        public String getKey() {
            return keys[index];
        }

        @Override
        public Object getValue() {
            return values[index];
        }

        @Override
        public Object setValue(Object value) {
            Object old = values[index];
            values[index] = value;
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry<?, ?> e)) {
                return false;
            }
            return keys[index].equals(e.getKey()) && Objects.equals(values[index], e.getValue());
        }

        @Override
        public int hashCode() {
            return keys[index].hashCode() ^ Objects.hashCode(values[index]);
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(size);
        for (int i = 0; i < usedSlots; i++) {
            if (keys[i] != null) {
                out.writeObject(keys[i]);
                out.writeObject(values[i]);
            }
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int count = in.readInt();
        init(tableSizeFor(Math.max((int) (count / LOAD_FACTOR) + 1, DEFAULT_CAPACITY)));
        for (int i = 0; i < count; i++) {
            String key = (String) in.readObject();
            Object value = in.readObject();
            put(key, value);
        }
    }

}
