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
package org.apache.camel.component.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The files that a poll left behind while a done file with a fixed name (such as {@code doneFileName=ready}) was there,
 * per done file (that is per directory), for one consumer.
 * <p/>
 * A file is left when the poll did not take it: beyond {@code maxMessagesPerPoll}, or its processing could not start
 * (for example the read lock was not acquired). The done file is then kept for the next poll, which only consumes the
 * files left: a file written after the done file waits for a new done file, as on a done file that had been deleted. A
 * file that was taken and failed is not left: as before, it waits for the next done file.
 * <p/>
 * The state is in memory and cleared when the consumer stops.
 */
final class GenericFileDoneFileAdmission {

    // done file -> files left by the last poll
    private final Map<String, Set<String>> left = new HashMap<>();

    /**
     * Whether the file may be consumed: all files may, unless the last poll left files of this done file, then only
     * these.
     */
    synchronized boolean isAdmitted(String doneFileName, String absoluteFileName) {
        Set<String> files = left.get(doneFileName);
        return files == null || files.contains(absoluteFileName);
    }

    /**
     * Called once the directory is listed, before the files are consumed: forgets the files left by the last poll.
     *
     * @param  listedDoneFiles the done files of the files this poll found
     * @return                 the done files kept for files that are gone (not found again), to delete
     */
    synchronized List<String> startBatch(Set<String> listedDoneFiles) {
        List<String> answer = null;
        for (String doneFileName : left.keySet()) {
            if (!listedDoneFiles.contains(doneFileName)) {
                if (answer == null) {
                    answer = new ArrayList<>();
                }
                answer.add(doneFileName);
            }
        }
        left.clear();
        return answer;
    }

    /**
     * Called for a file the poll did not take.
     */
    synchronized void left(String doneFileName, String absoluteFileName) {
        left.computeIfAbsent(doneFileName, k -> new HashSet<>()).add(absoluteFileName);
    }

    /**
     * Whether the poll left files of this done file, which must then be kept.
     */
    synchronized boolean hasLeft(String doneFileName) {
        return left.containsKey(doneFileName);
    }

    synchronized void clear() {
        left.clear();
    }
}
