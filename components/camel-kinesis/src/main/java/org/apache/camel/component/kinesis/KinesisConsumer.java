package org.apache.camel.component.kinesis;

import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ThrottlingException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.amazonaws.services.kinesis.model.Record;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static java.lang.String.format;
import static org.apache.camel.component.kinesis.KinesisConstants.BACKOFF_TIME_IN_MILLIS;
import static org.apache.camel.component.kinesis.KinesisConstants.NUM_RETRIES;

/**
 * Created by alina on 02.11.15.
 */
public class KinesisConsumer extends DefaultConsumer implements IRecordProcessorFactory {

    private final KinesisEndpoint endpoint;
    private final Processor processor;
    ExecutorService executor;


    public KinesisConsumer(KinesisEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
        this.endpoint = endpoint;
        if (StringUtils.isEmpty(endpoint.getRegion())) {
            throw new IllegalArgumentException("region must be specified");
        }
        if (StringUtils.isEmpty(endpoint.getApplicationName())) {
            throw new IllegalArgumentException("application name must be specified");
        }
        if (StringUtils.isEmpty(endpoint.getStreamName())) {
            throw new IllegalArgumentException("stream name must be specified");
        }
    }

    KinesisClientLibConfiguration getConf() throws UnknownHostException {
        return new KinesisClientLibConfiguration(
                endpoint.getApplicationName(),
                endpoint.getStreamName(),
                CredentialsProvider.getAwsSessionCredentialsProvider(),
                InetAddress.getLocalHost().getCanonicalHostName() + ":" + UUID.randomUUID())
                .withRegionName(endpoint.getRegion())
                .withInitialPositionInStream(InitialPositionInStream.LATEST);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.createExecutor()
                .submit(new Worker.Builder()
                        .recordProcessorFactory(this)
                        .config(getConf())
                        .build());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (executor != null) {
            executor.shutdown();
        }
        executor = null;
    }

    @Override
    public IRecordProcessor createProcessor() {
        return new KinessisRecordProcessor();
    }

    class KinessisRecordProcessor implements IRecordProcessor {
        private final Logger logger = LoggerFactory.getLogger(KinessisRecordProcessor.class);
        private String kinesisShardId;

        @Override
        public void initialize(InitializationInput initializationInput) {
            kinesisShardId = initializationInput.getShardId();
        }

        @Override
        public void processRecords(ProcessRecordsInput processRecordsInput) {
            logger.info(format("Processing %d records from %s", processRecordsInput.getRecords().size(), kinesisShardId));
            for (Record record : processRecordsInput.getRecords()) {
                Exchange kinesisExchange = endpoint.createKinesisExchange(record);
                try {
                    processor.process(kinesisExchange);
                } catch (Exception e) {
                    logger.error(format("Linesis consumer cannot process records from %s", kinesisShardId), e);
                }
            }

        }

        @Override
        public void shutdown(ShutdownInput shutdownInput) {
            try {
                checkpoint(shutdownInput.getCheckpointer());
            } catch (InterruptedException e) {
                logger.error("shutdown  checkpointer: ", e);
            }
        }

        private void checkpoint(IRecordProcessorCheckpointer checkpointer) throws InterruptedException {
            logger.info("Checkpointing shard " + kinesisShardId);
            for (int i = 0; i < NUM_RETRIES; i++) {
                try {
                    checkpointer.checkpoint();
                    break;
                } catch (ShutdownException e) {
                    logger.error("Caught shutdown exception, skipping checkpoint.", e);
                } catch (ThrottlingException e) {
                    logger.error(format("Checkpoint failed after %d attempts.", i), e);
                } catch (InvalidStateException e) {
                    logger.error("Cannot save checkpoint to the DynamoDB table used by " +
                            "the Amazon Kinesis Client Library.", e);
                } catch (Exception e) {
                    logger.error(format("Checkpointing shard%serror. ", kinesisShardId), e);
                }
                Thread.sleep(BACKOFF_TIME_IN_MILLIS);

            }
        }
    }
}


