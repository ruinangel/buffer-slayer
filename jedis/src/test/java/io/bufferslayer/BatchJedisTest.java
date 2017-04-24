package io.bufferslayer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jdeferred.Promise;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.util.SafeEncoder;

/**
 * Created by guohang.bao on 2017/4/24.
 */
@SuppressWarnings("unchecked")
public class BatchJedisTest {

  final byte[] bfoo = {0x01, 0x02, 0x03, 0x04};
  final byte[] bbar = {0x05, 0x06, 0x07, 0x08};

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Jedis jedis;
  private BatchJedis batchJedis;
  private AsyncReporter reporter;

  private Promise blocking(Promise promise) throws InterruptedException {
    reporter.flush();
    promise.waitSafely();
    promise.fail(f -> {
      throw new RuntimeException();
    });
    return promise;
  }

  @Before
  public void setup() {
    jedis = new Jedis("localhost", 6379);
    jedis.flushAll();

    reporter = AsyncReporter.builder(new JedisSender(jedis))
        .messageTimeout(0, TimeUnit.MILLISECONDS)
        .build();
    batchJedis = new BatchJedis(jedis, reporter);
  }

  @Test
  public void pipelined() {
    Jedis delegate = mock(Jedis.class);
    reporter = AsyncReporter.builder(new JedisSender(delegate))
        .messageTimeout(0, TimeUnit.MILLISECONDS)
        .build();

    batchJedis = new BatchJedis(delegate, reporter);

    Pipeline pipeline = mock(Pipeline.class);
    when(delegate.pipelined()).thenReturn(pipeline);

    batchJedis.append("foo", "bar");
    batchJedis.append("foo", "bar");
    reporter.flush();

    verify(delegate).pipelined();
    verify(pipeline, times(2))
        .append(SafeEncoder.encode("foo"), SafeEncoder.encode("bar"));
    verify(pipeline).syncAndReturnAll();
  }

  @Test
  public void append() throws InterruptedException {
    Promise promise = blocking(batchJedis.append("foo", "bar"));
    promise.done(value -> {
      assertEquals(3L, value);
      assertEquals("bar", jedis.get("foo"));
    });

    promise = blocking(batchJedis.append("foo", "bar"));
    promise.done(value -> {
      assertEquals(6L, value);
      assertEquals("barbar", jedis.get("foo"));
    });
  }

  @Test
  public void blpop() throws InterruptedException {
    Promise promise = blocking(batchJedis.blpop(1, "foo"));
    promise.done(Assert::assertNull);

    jedis.lpush("foo", "bar");
    promise = blocking(batchJedis.blpop(1, "foo"));
    promise.done(value -> {
      assertTrue(value instanceof List);
      List<String> result = (List<String>) value;
      assertEquals(2, result.size());
      assertEquals("foo", result.get(0));
      assertEquals("bar", result.get(1));
    });

    // Binary
    jedis.lpush(bfoo, bbar);
    promise = blocking(batchJedis.blpop(1, bfoo));
    promise.done(value -> {
      assertTrue(value instanceof List);
      List<byte[]> result = (List<byte[]>) value;
      assertEquals(2, result.size());
      assertArrayEquals(bfoo, result.get(0));
      assertArrayEquals(bbar, result.get(1));
    });
  }
}
