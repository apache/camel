package org.apache.camel.component.kinesis.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import static java.lang.String.format;

/**
 * Created by alina on 1.11.15.
 */
public class StringKinesisProducer extends CommonKinesisProducer<String> {
    private static final Logger log = LoggerFactory.getLogger(StringKinesisProducer.class);
    @Override
    public String getRegion() {
        return "us-west-2";
    }

    @Override
    public String getStreamName() {
        return "test";
    }

    @Override
    public String getPartitionKey() {
        return "test_partition_key";
    }

    @Override
    public ByteBuffer sendMessage(String message) {
        try {
            return ByteBuffer.wrap(message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.debug(format("Can't convert message '%s' to ByteBuffer", message), e);
            return ByteBuffer.wrap(new byte[0]);
        }
    }

    public static void main(String[] args) {
        StringKinesisProducer stringKinesisProducer = new StringKinesisProducer();
        stringKinesisProducer.execute("test message");

    }
}
