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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The done files with a fixed name (such as {@code doneFileName=ready}) that a consumer keeps because a poll left files
 * of their directory for the next poll.
 * <p/>
 * A file is left when the poll did not take it: beyond {@code maxMessagesPerPoll}, or its processing could not start
 * (for example the read lock was not acquired). The last file of the poll then does not delete the done file, so the
 * next poll consumes the files left, and any other file of the directory, as it does whenever the done file is there. A
 * file that was taken and failed is not left: as before, the done file is deleted after the last file of the poll
 * unless that file failed.
 * <p/>
 * The state is in memory and cleared when the consumer stops.
 */
final class GenericFileKeptDoneFiles {

    private final Set<String> kept = new HashSet<>();

    /**
     * Called once the directory is listed, before the files are consumed. When the listing was complete, forgets the
     * done files kept by the last poll, as the files of this poll decide again whether they are kept. When the listing
     * was cut (it stopped at the limit), the kept done files are left as they are: a done file not listed is still
     * kept, as its files may be among those not listed, and the caller then keeps the done file of every listed file,
     * as the poll leaves files for the next one.
     *
     * @param  listedDoneFiles the done files of the files this poll found
     * @param  listingComplete whether every file was listed
     * @return                 the done files kept for files that are gone (the listing was complete and found none of
     *                         their files), to delete
     */
    synchronized List<String> startBatch(Set<String> listedDoneFiles, boolean listingComplete) {
        List<String> answer = null;
        if (listingComplete) {
            for (String doneFileName : kept) {
                if (!listedDoneFiles.contains(doneFileName)) {
                    if (answer == null) {
                        answer = new ArrayList<>();
                    }
                    answer.add(doneFileName);
                }
            }
            kept.clear();
        }
        return answer;
    }

    /**
     * Keeps the done file, as this poll leaves files of its directory for the next poll.
     */
    synchronized void keep(String doneFileName) {
        kept.add(doneFileName);
    }

    /**
     * Whether the done file must be kept after the last file of the poll.
     */
    synchronized boolean isKept(String doneFileName) {
        return kept.contains(doneFileName);
    }

    synchronized void clear() {
        kept.clear();
    }
}
