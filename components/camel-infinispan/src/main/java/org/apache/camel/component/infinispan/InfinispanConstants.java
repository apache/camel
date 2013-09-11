package org.apache.camel.component.infinispan;

interface InfinispanConstants {
    String EVENT_TYPE = "CamelInfinispanEventType";
    String IS_PRE = "CamelInfinispanIsPre";
    String CACHE_NAME = "CamelInfinispanCacheName";
    String KEY = "CamelInfinispanKey";
    String VALUE = "CamelInfinispanValue";
    String OPERATION = "CamelInfinispanOperation";
    String PUT = "CamelInfinispanOperationPut";
    String GET = "CamelInfinispanOperationGet";
    String REMOVE = "CamelInfinispanOperationRemove";
    String CLEAR = "CamelInfinispanOperationClear";
    String RESULT = "CamelInfinispanOperationResult";
}
