package org.apache.camel.component.stitch.operations;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Message;
import org.apache.camel.component.stitch.StitchConfiguration;
import org.apache.camel.component.stitch.StitchConstants;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StitchProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(StitchProducerOperations.class);

    private final StitchClient client;
    private final StitchConfiguration configuration;

    public StitchProducerOperations(StitchClient client, StitchConfiguration configuration) {
        ObjectHelper.notNull(client, "client");
        ObjectHelper.notNull(configuration, "configuration");

        this.client = client;
        this.configuration = configuration;
    }

    public boolean sendEvents(
            final Message inMessage, final Consumer<List<StitchResponse>> resultCallback, final AsyncCallback callback) {

        return false;
    }

    private StitchRequestBody createStitchRecords(final Message inMessage) {
        // check if our exchange is list or contain some values
        if (inMessage.getBody() instanceof StitchRequestBody) {
            return createStitchRequestBodyFromStitchRequestBody(inMessage, inMessage.getBody(StitchRequestBody.class));
        }

        if (inMessage.getBody() instanceof StitchMessage) {
            return createStitchRequestBodyFromStitchMessage(inMessage, inMessage.getBody(StitchMessage.class));
        }

        if (inMessage.getBody() instanceof Iterable) {
            return null;
        }

        if (inMessage.getBody() instanceof Map) {
            return null;
        }

        // we have only a single event here
        return null;
    }

    private StitchRequestBody createStitchRequestBodyFromStitchRequestBody(
            final Message message, final StitchRequestBody requestBody) {
        return StitchRequestBody.fromStitchRequestBody(requestBody)
                .withSchema(getStitchSchema(message))
                .withTableName(getTableName(message))
                .withKeyNames(getKeyNames(message))
                .build();
    }

    private StitchRequestBody createStitchRequestBodyFromStitchMessage(
            final Message message, final StitchMessage stitchMessage) {
        return StitchRequestBody
                .builder()
                .addMessage(stitchMessage)
                .withKeyNames(getKeyNames(message))
                .withSchema(getStitchSchema(message))
                .withTableName(getTableName(message))
                .withKeyNames(getKeyNames(message))
                .build();
    }

    private StitchRequestBody createStitchRecordFromMap(final Message message, final Map<String, Object> data) {
        // let's override things
        if (ObjectHelper.isNotEmpty(getTableName(message))) {
            data.put(StitchRequestBody.TABLE_NAME, getTableName(message));
        }

        return StitchRequestBody.fromMap(data).build();
    }

    private String getTableName(final Message message) {
        return getOption(message, StitchConstants.TABLE_NAME, configuration::getTableName, String.class);
    }

    @SuppressWarnings("unchecked")
    private StitchSchema getStitchSchema(final Message message) {
        // if we have header set, then we try first that
        if (ObjectHelper.isNotEmpty(message.getHeader(StitchConstants.SCHEMA))) {
            if (message.getHeader(StitchConstants.SCHEMA) instanceof StitchSchema) {
                return message.getHeader(StitchConstants.SCHEMA, StitchSchema.class);
            }
            if (message.getHeader(StitchConstants.SCHEMA) instanceof Map) {
                return StitchSchema.builder().addKeywords(message.getHeader(StitchConstants.SCHEMA, Map.class)).build();
            }
        }
        // otherwise we just get whatever we have in the config
        return configuration.getStitchSchema();
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getKeyNames(final Message message) {
        return getOption(message, StitchConstants.KEY_NAMES, configuration::getKeyNames, Collection.class);
    }

    private <R> R getOption(
            final Message message, final String headerName, final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(message) || ObjectHelper.isEmpty(getObjectFromHeaders(message, headerName, type))
                ? fallbackFn.get()
                : getObjectFromHeaders(message, headerName, type);
    }

    private <T> T getObjectFromHeaders(final Message message, final String headerName, final Class<T> classType) {
        return message.getHeader(headerName, classType);
    }
}
