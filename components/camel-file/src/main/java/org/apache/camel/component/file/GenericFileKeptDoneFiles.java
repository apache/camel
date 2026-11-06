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
 * The files of a poll can complete on another thread while the next poll runs (the route continues asynchronously), so
 * the last file of a poll checks the done files kept by its own poll as well as those kept by the current poll (see
 * {@link Batch}).
 * <p/>
 * The state is in memory, and the done files kept by the current poll are forgotten when the consumer stops.
 */
final class GenericFileKeptDoneFiles {

    // the done files kept by the current poll: a complete listing starts a new set, and the set of the previous poll
    // stays with the files of that poll that have not completed yet
    private Set<String> kept = new HashSet<>();

    /**
     * Called once the directory is listed, before the files are consumed. When the listing was complete, forgets the
     * done files kept by the last poll, as the files of this poll decide again whether they are kept; the files of the
     * last poll that have not completed yet still find them kept (see {@link #batch()}). When the listing was cut (it
     * stopped at the limit), the kept done files are left as they are: a done file not listed is still kept, as its
     * files may be among those not listed, and the caller then keeps the done file of every listed file, as the poll
     * leaves files for the next one.
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
            kept = new HashSet<>();
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
     * Whether the current poll keeps the done file (the files of a poll check it through {@link #batch()}).
     */
    synchronized boolean isKept(String doneFileName) {
        return kept.contains(doneFileName);
    }

    /**
     * The kept done files as the files of the current poll see them, to take when a file of this poll is started.
     */
    synchronized Batch batch() {
        return new Batch(kept);
    }

    synchronized void clear() {
        kept.clear();
    }

    /**
     * The kept done files as the files of one poll see them.
     */
    final class Batch {

        private final Set<String> keptByBatch;

        private Batch(Set<String> keptByBatch) {
            this.keptByBatch = keptByBatch;
        }

        /**
         * Whether the done file must be kept after the last file of this poll: this poll or the current one left files
         * of its directory. A file of this poll that completes while the next poll runs must not delete a done file
         * that this poll kept before the next poll has decided again whether it keeps it.
         */
        boolean isKept(String doneFileName) {
            synchronized (GenericFileKeptDoneFiles.this) {
                return keptByBatch.contains(doneFileName) || kept.contains(doneFileName);
            }
        }
    }
}
