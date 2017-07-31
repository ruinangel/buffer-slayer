package io.github.tramchamploo.bufferslayer;

import com.github.mauricio.async.db.Configuration;
import com.github.mauricio.async.db.mysql.util.URLParser;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import java.beans.PropertyVetoException;
import java.nio.charset.Charset;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Jdbc benchmark that simply doing inserts to a single table.
 */
public abstract class AbstractVertxJdbcBenchmark {

  private AsyncSQLClient batch;
  private AsyncSQLClient unbatch;
  private Reporter<Statement, UpdateResult> reporter;
  private static AsyncSenderProxy proxy;
  private static AtomicLong counter = new AtomicLong();

  private static final String CREATE_DATABASE = "CREATE DATABASE IF NOT EXISTS test";
  private static final String CREATE_TABLE = "CREATE TABLE test.benchmark(id INT PRIMARY KEY AUTO_INCREMENT, data VARCHAR(32), time TIMESTAMP)";
  private static final String DROP_TABLE = "DROP TABLE IF EXISTS test.benchmark";
  private static final String TRUNCATE_TABLE = "TRUNCATE TABLE test.benchmark";
  private static final String INSERTION = "INSERT INTO test.benchmark(data, time) VALUES(?, now())";

  static String propertyOr(String key, String fallback) {
    return System.getProperty(key, fallback);
  }

  protected abstract Reporter<Statement, UpdateResult> reporter(Sender<Statement, UpdateResult> sender);

  @Setup
  public void setup() throws PropertyVetoException {
    Configuration jdbcUrl = URLParser.parse(propertyOr("jdbcUrl", "jdbc:mysql://127.0.0.1:3306?useSSL=false"), Charset.forName("UTF-8"));
    JsonObject mySQLClientConfig = new JsonObject()
        .put("host", jdbcUrl.host())
        .put("port", jdbcUrl.port())
        .put("username", propertyOr("username", "root"))
        .put("password", propertyOr("password", "root"))
        .put("database", "");

    Vertx vertx = Vertx.vertx();
    unbatch = MySQLClient.createShared(vertx, mySQLClientConfig);

    proxy = new AsyncSenderProxy(new SQLConnectionSender(unbatch));
    proxy.onMessages(updated -> {
      Integer rows = updated.stream().map(UpdateResult::getUpdated).reduce(0, (a, b) -> a + b);
      counter.addAndGet(rows);
    });

    reporter = reporter(proxy);
    batch = BatchMySQLClient.wrap(unbatch, reporter);

    unbatch.getConnection(connection -> {
      SQLConnection conn = connection.result();
      conn.update(CREATE_DATABASE, cd -> {
        System.out.println("CREATE DATABASE: " + cd.succeeded());
        conn.update(DROP_TABLE, dt -> {
          System.out.println("DROP TABLE: " + dt.succeeded());
          conn.update(CREATE_TABLE, ct -> {
            System.out.println("CREATE TABLE: " + ct.succeeded());
            conn.close();
          });
        });
      });
    });
  }

  @TearDown(Level.Iteration)
  public void truncateTable() {
    unbatch.getConnection(connection -> {
      SQLConnection conn = connection.result();
      conn.update(TRUNCATE_TABLE, result -> conn.close());
    });
  }

  @AuxCounters
  @State(Scope.Thread)
  public static class AtomicLongCounter {

    public long updated() {
      return counter.get();
    }

    @Setup(Level.Iteration)
    public void clean() {
      counter.set(0);
    }
  }

  @State(Scope.Benchmark)
  public static class Lagging {

    @Setup(Level.Iteration)
    public void lag() throws InterruptedException {
      TimeUnit.MILLISECONDS.sleep(500);
    }
  }

  static String randomString() {
    return String.valueOf(ThreadLocalRandom.current().nextLong());
  }

  private void doUpdate(AsyncSQLClient client) {
    client.getConnection(connection -> {
      SQLConnection conn = connection.result();
      conn.updateWithParams(INSERTION, new JsonArray().add(randomString()), result -> conn.close());
    });
  }

  @Benchmark @Group("no_contention_batched") @GroupThreads(1)
  public void no_contention_batched_insert(Lagging l, AtomicLongCounter counters) {
    doUpdate(batch);
  }

  @Benchmark @Group("no_contention_unbatched") @GroupThreads(1)
  public void no_contention_unbatched_insert(Lagging l) {
    doUpdate(unbatch);
  }

  @Benchmark @Group("mild_contention_batched") @GroupThreads(2)
  public void mild_contention_batched_insert(Lagging l, AtomicLongCounter counters) {
    doUpdate(batch);
  }

  @Benchmark @Group("mild_contention_unbatched") @GroupThreads(2)
  public void mild_contention_unbatched_insert(Lagging l) {
    doUpdate(unbatch);
  }

  @Benchmark @Group("high_contention_batched") @GroupThreads(8)
  public void high_contention_batched_insert(Lagging l, AtomicLongCounter counters) {
    doUpdate(batch);
  }

  @Benchmark @Group("high_contention_unbatched") @GroupThreads(8)
  public void high_contention_unbatched_insert(Lagging l) {
    doUpdate(unbatch);
  }
}
