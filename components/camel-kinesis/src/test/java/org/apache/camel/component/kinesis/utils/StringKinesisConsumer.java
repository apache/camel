package org.apache.camel.component.kinesis.utils;

import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;

import java.net.UnknownHostException;

/**
 * Created by alina on 02.11.15.
 */
public class StringKinesisConsumer extends CommonKinesisConsumer {
    @Override
    public String getApplicationName() {
        return "CommonKinesisConsumer";
    }

    @Override
    public String getRegion() {
        return "us-west-2";
    }

    @Override
    public String getStreamName() {
        return "test";
    }

    @Override
    public IRecordProcessorFactory getRecordProcessorFactory() {
        return this;
    }

    @Override
    public IRecordProcessor createProcessor() {
        return new StringRecordProcessor();
    }

    public static void main(String... args) throws UnknownHostException {
        StringKinesisConsumer stringKinesisConsumer = new StringKinesisConsumer();
        stringKinesisConsumer.execute();
    }
}
