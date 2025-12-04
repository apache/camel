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

package org.apache.camel.component.tahu.handlers;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.FileUtils;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class CamelBdSeqManager implements BdSeqManager {

    private static final Logger LOG = LoggerFactory.getLogger(CamelBdSeqManager.class);

    private static final Charset bdSeqNumFileCharset = StandardCharsets.UTF_8;

    private final File bdSeqNumFile;

    private final Marker loggingMarker;

    public CamelBdSeqManager(EdgeNodeDescriptor edgeNodeDescriptor, String bdSeqNumPath) {
        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        if (ObjectHelper.isEmpty(bdSeqNumPath)) {
            bdSeqNumPath = FileUtils.getTempDirectoryPath() + File.separator + "CamelTahuTemp";
        }

        String bdSeqNumFileName =
                bdSeqNumPath + File.separator + edgeNodeDescriptor.getDescriptorString() + "-bdSeqNum";

        bdSeqNumFile = new File(bdSeqNumFileName);
    }

    // This method is NOT intended to increment the stored value, only to retrieve
    // it
    @Override
    public long getNextDeathBdSeqNum() {
        try {
            long bdSeqNum = 0L;
            if (bdSeqNumFile.exists() && FileUtils.sizeOf(bdSeqNumFile) > 0L) {
                String bdSeqFileContents = FileUtils.readFileToString(bdSeqNumFile, bdSeqNumFileCharset);

                bdSeqNum = normalizeBdSeq(Long.parseLong(bdSeqFileContents));

                LOG.debug(loggingMarker, "Next Death bdSeq number: {}", bdSeqNum);
            }
            return bdSeqNum;
        } catch (Exception e) {
            LOG.warn(loggingMarker, "Failed to get the bdSeq number from the persistent directory", e);
            storeNextDeathBdSeqNum(0);
            return 0;
        }
    }

    @Override
    public void storeNextDeathBdSeqNum(long bdSeqNum) {
        try {
            String bdSeqFileContents = Long.toString(normalizeBdSeq(bdSeqNum));
            FileUtils.writeStringToFile(bdSeqNumFile, bdSeqFileContents, bdSeqNumFileCharset, false);
        } catch (Exception e) {
            LOG.error(loggingMarker, "Failed to write the bdSeq number to the persistent directory", e);
        }
    }

    private long normalizeBdSeq(long bdSeqNum) {
        // bdSeqNum valid range is 0-255
        return bdSeqNum & 0xFF;
    }
}
