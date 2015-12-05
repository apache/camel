package org.apache.camel.component.kinesis.utils;

import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.amazonaws.services.kinesis.model.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * Created by alina on 02.11.15.
 */
public class StringRecordProcessor implements IRecordProcessor {
    private static final Logger log = LoggerFactory.getLogger(StringRecordProcessor.class);
    private String shardId;

    @Override
    public void initialize(InitializationInput initializationInput) {
        shardId = initializationInput.getShardId();
    }

    @Override
    public void processRecords(ProcessRecordsInput processRecordsInput) {
        int size = processRecordsInput.getRecords().size();
        log.info("Records count: " + size);
        for (Record record : processRecordsInput.getRecords()) {
            log.info(format("message: %sfrom shard : %s", new String(record.getData().array()), shardId));
        }
    }

    @Override
    public void shutdown(ShutdownInput shutdownInput) {}
}