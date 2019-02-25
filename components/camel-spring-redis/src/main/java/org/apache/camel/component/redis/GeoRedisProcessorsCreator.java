package org.apache.camel.component.redis;

import java.util.Map;

import org.apache.camel.Processor;

public final class GeoRedisProcessorsCreator extends AbstractRedisProcessorCreator {

    Map<Command, Processor> getProcessors(RedisClient redisClient, ExchangeConverter exchangeConverter) {
        bind(Command.GEOADD, wrap(exchange -> redisClient.geoadd(exchangeConverter.getKey(exchange), exchangeConverter.getLongitude(exchange), exchangeConverter.getLatitude(exchange), exchangeConverter.getValue(exchange))));
        bind(Command.GEODIST, wrap(exchange -> redisClient.geodist(exchangeConverter.getKey(exchange), exchangeConverter.getValuesAsCollection(exchange).toArray()[0], exchangeConverter.getValuesAsCollection(exchange).toArray()[1])));
        bind(Command.GEOHASH, wrap(exchange -> redisClient.geohash(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange))));
        bind(Command.GEOPOS, wrap(exchange -> redisClient.geopos(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange))));
        bind(Command.GEORADIUS, wrap(exchange -> redisClient.georadius(exchangeConverter.getKey(exchange), exchangeConverter.getLongitude(exchange), exchangeConverter.getLatitude(exchange), exchangeConverter.getRadius(exchange), exchangeConverter.getCount(exchange))));
        bind(Command.GEORADIUSBYMEMBER, wrap(exchange -> redisClient.georadius(exchangeConverter.getKey(exchange), exchangeConverter.getValue(exchange), exchangeConverter.getRadius(exchange), exchangeConverter.getCount(exchange))));
        return result;
    }
}