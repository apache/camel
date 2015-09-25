package org.apache.camel.component.redis.processor.idempotent;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RedisStringIdempotentRepository extends RedisIdempotentRepository {

  private final ValueOperations<String, String> valueOperations;

  /*
  The expiry time frame for the item in seconds
   */
  private Long expiry;

  public RedisStringIdempotentRepository(
      RedisTemplate<String, String> redisTemplate,
      String processorName) {
    super(redisTemplate, processorName);
    this.valueOperations = redisTemplate.opsForValue();
  }

  @Override
  public boolean contains(String key) {
    String value = valueOperations.get(createRedisKey(key));
    if (value != null) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean add(String key) {
    boolean added = valueOperations.setIfAbsent(createRedisKey(key), key);
    if (expiry != null) {
      valueOperations.getOperations().expire(createRedisKey(key), expiry, TimeUnit.SECONDS);
    }
    return added;
  }

  @Override
  public boolean remove(String key) {
    valueOperations.getOperations().delete(createRedisKey(key));
    return true;
  }

  public void clear() {
    valueOperations.getOperations().execute(new RedisCallback<List<byte[]>>() {
      @Override
      public List<byte[]> doInRedis(RedisConnection connection) throws DataAccessException {
        List<byte[]> binaryKeys = new ArrayList<>();
        Cursor<byte[]>
            cursor =
            connection.scan(ScanOptions.scanOptions().match("*" + createRedisKey("*")).build());

        while (cursor.hasNext()) {
          byte[] key = cursor.next();
          binaryKeys.add(key);
        }
        if (binaryKeys.size() > 0) {
          connection.del(binaryKeys.toArray(new byte[][]{}));
        }
        return binaryKeys;
      }
    });
  }

  public String createRedisKey(String key) {
    return new StringBuilder(getProcessorName()).append(":").append(key).toString();
  }

  public Long getExpiry() {
    return expiry;
  }

  /**
   * Exire all newly added items after the given number of seconds
   */
  public void setExpiry(Long expiry) {
    this.expiry = expiry;
  }
}
