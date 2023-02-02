package org.apache.camel.utils.cassandra;

import com.datastax.oss.driver.api.core.type.codec.ExtraTypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;

public enum CassandraExtraCodecs {

    BLOB_TO_ARRAY(ExtraTypeCodecs.BLOB_TO_ARRAY),
    BOOLEAN_LIST_TO_ARRAY(ExtraTypeCodecs.BOOLEAN_LIST_TO_ARRAY),
    BYTE_LIST_TO_ARRAY(ExtraTypeCodecs.BYTE_LIST_TO_ARRAY),
    SHORT_LIST_TO_ARRAY(ExtraTypeCodecs.SHORT_LIST_TO_ARRAY),
    INT_LIST_TO_ARRAY(ExtraTypeCodecs.INT_LIST_TO_ARRAY),
    LONG_LIST_TO_ARRAY(ExtraTypeCodecs.LONG_LIST_TO_ARRAY),
    FLOAT_LIST_TO_ARRAY(ExtraTypeCodecs.FLOAT_LIST_TO_ARRAY),
    DOUBLE_LIST_TO_ARRAY(ExtraTypeCodecs.DOUBLE_LIST_TO_ARRAY),
    TIMESTAMP_UTC(ExtraTypeCodecs.TIMESTAMP_UTC),
    TIMESTAMP_MILLIS_SYSTEM(ExtraTypeCodecs.TIMESTAMP_MILLIS_SYSTEM),
    TIMESTAMP_MILLIS_UTC(ExtraTypeCodecs.TIMESTAMP_MILLIS_UTC),
    ZONED_TIMESTAMP_SYSTEM(ExtraTypeCodecs.ZONED_TIMESTAMP_SYSTEM),
    ZONED_TIMESTAMP_UTC(ExtraTypeCodecs.ZONED_TIMESTAMP_UTC),
    ZONED_TIMESTAMP_PERSISTED(ExtraTypeCodecs.ZONED_TIMESTAMP_PERSISTED),
    LOCAL_TIMESTAMP_SYSTEM(ExtraTypeCodecs.LOCAL_TIMESTAMP_SYSTEM),
    LOCAL_TIMESTAMP_UTC(ExtraTypeCodecs.LOCAL_TIMESTAMP_UTC);

    private final TypeCodec codec;

    private CassandraExtraCodecs(TypeCodec codec) {
        this.codec = codec;
    }

    public TypeCodec codec() {
        return codec;
    }
}
