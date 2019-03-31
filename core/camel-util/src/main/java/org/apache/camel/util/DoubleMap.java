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

import java.util.function.Predicate;

import org.apache.camel.util.function.TriConsumer;

@SuppressWarnings("unchecked")
public class DoubleMap<K1, K2, V> {

    private static final double MAX_LOAD_FACTOR = 1.2;
    private static final int MAX_TABLE_SIZE = 32768;
    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    static class Entry {
        Object k1;
        Object k2;
        Object v;
        Entry next;
    }

    private Entry[] table;
    private int mask;

    public DoubleMap(int size) {
        table = new Entry[closedTableSize(size)];
        mask = table.length - 1;
    }

    public V get(K1 k1, K2 k2) {
        Entry[] table = this.table;
        int mask = this.mask;
        int index = smear(k1.hashCode() * 31 + k2.hashCode()) & mask;
        for (Entry entry = table[index]; entry != null; entry = entry.next) {
            if (k1 == entry.k1 && k2 == entry.k2) {
                return (V) entry.v;
            }
        }
        return null;
    }

    public void forEach(TriConsumer<K1, K2, V> consumer) {
        Entry[] table = this.table;
        for (Entry entry : table) {
            while (entry != null) {
                consumer.accept((K1) entry.k1, (K2) entry.k2, (V) entry.v);
                entry = entry.next;
            }
        }
    }

    public boolean containsKey(K1 k1, K2 k2) {
        Entry[] table = this.table;
        int mask = this.mask;
        int index = smear(k1.hashCode() * 31 + k2.hashCode()) & mask;
        for (Entry entry = table[index]; entry != null; entry = entry.next) {
            if (k1 == entry.k1 && k2 == entry.k2) {
                return true;
            }
        }
        return false;
    }

    public synchronized void put(K1 k1, K2 k2, V v) {
        Entry[] table = this.table;
        int size = size() + 1;
        int realSize = closedTableSize(size);
        if (realSize <= table.length) {
            realSize = table.length;
            int index = smear(k1.hashCode() * 31 + k2.hashCode()) & (realSize - 1);
            for (Entry oldEntry = table[index]; oldEntry != null; oldEntry = oldEntry.next) {
                if (oldEntry.k1 == k1 && oldEntry.k2 == k2) {
                    oldEntry.v = v;
                    return;
                }
            }
            Entry entry = new Entry();
            entry.k1 = k1;
            entry.k2 = k2;
            entry.v = v;
            entry.next = table[index];
            table[index] = entry;
        } else {
            Entry[] newT = new Entry[realSize];
            int index = smear(k1.hashCode() * 31 + k2.hashCode()) & (realSize - 1);
            Entry entry = new Entry();
            newT[index] = entry;
            entry.k1 = k1;
            entry.k2 = k2;
            entry.v = v;
            for (Entry oldEntry : table) {
                while (oldEntry != null) {
                    if (k1 != oldEntry.k1 || k2 != oldEntry.k2) {
                        index = smear(oldEntry.k1.hashCode() * 31 + oldEntry.k2.hashCode()) & (realSize - 1);
                        Entry newEntry = new Entry();
                        newEntry.k1 = oldEntry.k1;
                        newEntry.k2 = oldEntry.k2;
                        newEntry.v = oldEntry.v;
                        newEntry.next = newT[index];
                        newT[index] = newEntry;
                    }
                    oldEntry = oldEntry.next;
                }
            }
            this.table = newT;
            this.mask = realSize - 1;
        }
    }

    public synchronized boolean remove(K1 k1, K2 k2) {
        Entry[] table = this.table;
        int mask = this.mask;
        int index = smear(k1.hashCode() * 31 + k2.hashCode()) & mask;
        Entry prevEntry = null;
        for (Entry oldEntry = table[index]; oldEntry != null; prevEntry = oldEntry, oldEntry = oldEntry.next) {
            if (oldEntry.k1 == k1 && oldEntry.k2 == k2) {
                if (prevEntry == null) {
                    table[index] = oldEntry.next;
                } else {
                    prevEntry.next = oldEntry.next;
                }
                return true;
            }
        }
        return false;
    }

    public V getFirst(Predicate<K1> p1, Predicate<K2> p2) {
        for (Entry entry : table) {
            while (entry != null) {
                if (p1.test((K1) entry.k1) && p2.test((K2) entry.k2)) {
                    return (V) entry.v;
                }
                entry = entry.next;
            }
        }
        return null;
    }

    public int size() {
        Entry[] table = this.table;
        int n = 0;
        if (table != null) {
            for (Entry e : table) {
                for (Entry c = e; c != null; c = c.next) {
                    n++;
                }
            }
        }
        return n;
    }

    public synchronized void clear() {
        this.table = new Entry[table.length];
    }

    static int smear(int hashCode) {
        return C2 * Integer.rotateLeft(hashCode * C1, 15);
    }

    static int closedTableSize(int expectedEntries) {
        // Get the recommended table size.
        // Round down to the nearest power of 2.
        expectedEntries = Math.max(expectedEntries, 2);
        int tableSize = Integer.highestOneBit(expectedEntries);
        // Check to make sure that we will not exceed the maximum load factor.
        if (expectedEntries > (int) (MAX_LOAD_FACTOR * tableSize)) {
            tableSize <<= 1;
            return (tableSize > 0) ? tableSize : MAX_TABLE_SIZE;
        }
        return tableSize;
    }

}
