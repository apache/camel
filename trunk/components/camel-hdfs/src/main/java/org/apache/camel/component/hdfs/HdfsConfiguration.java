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
package org.apache.camel.component.hdfs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.URISupport;
import org.apache.hadoop.io.SequenceFile;

public class HdfsConfiguration {

    private URI uri;
    private String hostName;
    private int port = HdfsConstants.DEFAULT_PORT;
    private String path;
    private boolean overwrite = true;
    private boolean append;
    private int bufferSize = HdfsConstants.DEFAULT_BUFFERSIZE;
    private short replication = HdfsConstants.DEFAULT_REPLICATION;
    private long blockSize = HdfsConstants.DEFAULT_BLOCKSIZE;
    private SequenceFile.CompressionType compressionType = HdfsConstants.DEFAULT_COMPRESSIONTYPE;
    private HdfsCompressionCodec compressionCodec = HdfsConstants.DEFAULT_CODEC;
    private HdfsFileType fileType = HdfsFileType.NORMAL_FILE;
    private HdfsFileSystemType fileSystemType = HdfsFileSystemType.HDFS;
    private HdfsWritableFactories.WritableType keyType = HdfsWritableFactories.WritableType.NULL;
    private HdfsWritableFactories.WritableType valueType = HdfsWritableFactories.WritableType.BYTES;
    private String openedSuffix = HdfsConstants.DEFAULT_OPENED_SUFFIX;
    private String readSuffix = HdfsConstants.DEFAULT_READ_SUFFIX;
    private long initialDelay;
    private long delay = HdfsConstants.DEFAULT_DELAY;
    private String pattern = HdfsConstants.DEFAULT_PATTERN;
    private int chunkSize = HdfsConstants.DEFAULT_BUFFERSIZE;
    private int checkIdleInterval = HdfsConstants.DEFAULT_CHECK_IDLE_INTERVAL;
    private List<HdfsProducer.SplitStrategy> splitStrategies;

    public HdfsConfiguration() {
    }

    private Boolean getBoolean(Map hdfsSettings, String param, Boolean dflt) {
        if (hdfsSettings.containsKey(param)) {
            return Boolean.valueOf((String) hdfsSettings.get(param));
        } else {
            return dflt;
        }
    }

    private Integer getInteger(Map hdfsSettings, String param, Integer dflt) {
        if (hdfsSettings.containsKey(param)) {
            return Integer.valueOf((String) hdfsSettings.get(param));
        } else {
            return dflt;
        }
    }

    private Short getShort(Map hdfsSettings, String param, Short dflt) {
        if (hdfsSettings.containsKey(param)) {
            return Short.valueOf((String) hdfsSettings.get(param));
        } else {
            return dflt;
        }
    }

    private Long getLong(Map hdfsSettings, String param, Long dflt) {
        if (hdfsSettings.containsKey(param)) {
            return Long.valueOf((String) hdfsSettings.get(param));
        } else {
            return dflt;
        }
    }

    private HdfsFileType getFileType(Map hdfsSettings, String param, HdfsFileType dflt) {
        String eit = (String) hdfsSettings.get(param);
        if (eit != null) {
            return HdfsFileType.valueOf(eit);
        } else {
            return dflt;
        }
    }

    private HdfsFileSystemType getFileSystemType(Map hdfsSettings, String param, HdfsFileSystemType dflt) {
        String eit = (String) hdfsSettings.get(param);
        if (eit != null) {
            return HdfsFileSystemType.valueOf(eit);
        } else {
            return dflt;
        }
    }

    private HdfsWritableFactories.WritableType getWritableType(Map hdfsSettings, String param, HdfsWritableFactories.WritableType dflt) {
        String eit = (String) hdfsSettings.get(param);
        if (eit != null) {
            return HdfsWritableFactories.WritableType.valueOf(eit);
        } else {
            return dflt;
        }
    }

    private SequenceFile.CompressionType getCompressionType(Map hdfsSettings, String param, SequenceFile.CompressionType ct) {
        String eit = (String) hdfsSettings.get(param);
        if (eit != null) {
            return SequenceFile.CompressionType.valueOf(eit);
        } else {
            return ct;
        }
    }

    private HdfsCompressionCodec getCompressionCodec(Map hdfsSettings, String param, HdfsCompressionCodec cd) {
        String eit = (String) hdfsSettings.get(param);
        if (eit != null) {
            return HdfsCompressionCodec.valueOf(eit);
        } else {
            return cd;
        }
    }

    private String getString(Map hdfsSettings, String param, String dflt) {
        if (hdfsSettings.containsKey(param)) {
            return (String) hdfsSettings.get(param);
        } else {
            return dflt;
        }
    }

    private List<HdfsProducer.SplitStrategy> getSplitStrategies(Map hdfsSettings) {
        List<HdfsProducer.SplitStrategy> strategies = new ArrayList<HdfsProducer.SplitStrategy>();
        for (Object obj : hdfsSettings.keySet()) {
            String key = (String) obj;
            if ("splitStrategy".equals(key)) {
                String eit = (String) hdfsSettings.get(key);
                if (eit != null) {
                    String[] strstrategies = eit.split(",");
                    for (int i = 0; i < strstrategies.length; i++) {
                        String strstrategy = strstrategies[i];
                        String tokens[] = strstrategy.split(":");
                        if (tokens.length != 2) {
                            throw new IllegalArgumentException("Wrong Split Strategy " + key + "=" + eit);
                        }
                        HdfsProducer.SplitStrategyType sst = HdfsProducer.SplitStrategyType.valueOf(tokens[0]);
                        long ssv = Long.valueOf(tokens[1]);
                        strategies.add(new HdfsProducer.SplitStrategy(sst, ssv));
                    }
                }
            }
        }
        return strategies;
    }

    public void checkConsumerOptions() {
    }

    public void checkProducerOptions() {
        if (isAppend()) {
            if (getSplitStrategies().size() != 0) {
                throw new IllegalArgumentException("Split Strategies incompatible with append=true");
            }
            if (getFileType() != HdfsFileType.NORMAL_FILE) {
                throw new IllegalArgumentException("append=true works only with NORMAL_FILEs");
            }
        }
    }

    public void parseURI(URI uri) throws URISyntaxException {
        String protocol = uri.getScheme();
        if (!protocol.equalsIgnoreCase("hdfs")) {
            throw new IllegalArgumentException("Unrecognized Cache protocol: " + protocol + " for uri: " + uri);
        }
        hostName = uri.getHost();
        port = uri.getPort() == -1 ? HdfsConstants.DEFAULT_PORT : uri.getPort();
        path = uri.getPath();
        Map hdfsSettings = URISupport.parseParameters(uri);

        overwrite = getBoolean(hdfsSettings, "overwrite", overwrite);
        append = getBoolean(hdfsSettings, "append", append);
        bufferSize = getInteger(hdfsSettings, "bufferSize", bufferSize);
        replication = getShort(hdfsSettings, "replication", replication);
        blockSize = getLong(hdfsSettings, "blockSize", blockSize);
        compressionType = getCompressionType(hdfsSettings, "compressionType", compressionType);
        compressionCodec = getCompressionCodec(hdfsSettings, "compressionCodec", compressionCodec);
        fileType = getFileType(hdfsSettings, "fileType", fileType);
        fileSystemType = getFileSystemType(hdfsSettings, "fileSystemType", fileSystemType);
        keyType = getWritableType(hdfsSettings, "keyType", keyType);
        valueType = getWritableType(hdfsSettings, "valueType", valueType);
        openedSuffix = getString(hdfsSettings, "openedSuffix", openedSuffix);
        readSuffix = getString(hdfsSettings, "readSuffix", readSuffix);
        initialDelay = getLong(hdfsSettings, "initialDelay", initialDelay);
        delay = getLong(hdfsSettings, "delay", delay);
        pattern = getString(hdfsSettings, "pattern", pattern);
        chunkSize = getInteger(hdfsSettings, "chunkSize", chunkSize);
        splitStrategies = getSplitStrategies(hdfsSettings);
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public short getReplication() {
        return replication;
    }

    public void setReplication(short replication) {
        this.replication = replication;
    }

    public long getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(long blockSize) {
        this.blockSize = blockSize;
    }

    public HdfsFileType getFileType() {
        return fileType;
    }

    public void setFileType(HdfsFileType fileType) {
        this.fileType = fileType;
    }

    public SequenceFile.CompressionType getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(SequenceFile.CompressionType compressionType) {
        this.compressionType = compressionType;
    }

    public HdfsCompressionCodec getCompressionCodec() {
        return compressionCodec;
    }

    public void setCompressionCodec(HdfsCompressionCodec compressionCodec) {
        this.compressionCodec = compressionCodec;
    }

    public void setFileSystemType(HdfsFileSystemType fileSystemType) {
        this.fileSystemType = fileSystemType;
    }

    public HdfsFileSystemType getFileSystemType() {
        return fileSystemType;
    }

    public HdfsWritableFactories.WritableType getKeyType() {
        return keyType;
    }

    public void setKeyType(HdfsWritableFactories.WritableType keyType) {
        this.keyType = keyType;
    }

    public HdfsWritableFactories.WritableType getValueType() {
        return valueType;
    }

    public void setValueType(HdfsWritableFactories.WritableType valueType) {
        this.valueType = valueType;
    }

    public void setOpenedSuffix(String openedSuffix) {
        this.openedSuffix = openedSuffix;
    }

    public String getOpenedSuffix() {
        return openedSuffix;
    }

    public void setReadSuffix(String readSuffix) {
        this.readSuffix = readSuffix;
    }

    public String getReadSuffix() {
        return readSuffix;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public long getDelay() {
        return delay;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setCheckIdleInterval(int checkIdleInterval) {
        this.checkIdleInterval = checkIdleInterval;
    }

    public int getCheckIdleInterval() {
        return checkIdleInterval;
    }

    public List<HdfsProducer.SplitStrategy> getSplitStrategies() {
        return splitStrategies;
    }

    public void setSplitStrategy(String splitStrategy) {
        // noop
    }
}
