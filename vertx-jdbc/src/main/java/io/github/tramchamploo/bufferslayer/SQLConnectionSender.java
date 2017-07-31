package io.github.tramchamploo.bufferslayer;

import com.google.common.base.Preconditions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

/**
 * Execute sqls using vertx async mysql client
 */
final class SQLConnectionSender implements AsyncSender<Statement, UpdateResult> {

  private final AsyncSQLClient underlying;

  SQLConnectionSender(AsyncSQLClient underlying) {
    this.underlying = underlying;
  }

  @Override
  public Promise<List<UpdateResult>, MessageDroppedException, ?> send(List<Statement> statements) {
    Preconditions.checkNotNull(statements);
    if (!sameSQL(statements)) {
      throw new UnsupportedOperationException("Different SQLs are not supported");
    }

    boolean allWithArgs = allWithArgs(statements);
    Deferred<List<UpdateResult>, MessageDroppedException, ?> deferred = new DeferredObject<>();

    underlying.getConnection(connection -> {
      SQLConnection conn = connection.result();
      Handler<AsyncResult<List<Integer>>> handler = result -> {
        try {
          if (result.succeeded()) {
            deferred.resolve(toUpdateResults(result.result()));
          } else {
            deferred.reject(MessageDroppedException.dropped(result.cause(), statements));
          }
        } finally {
          conn.close();
        }
      };

      if (allWithArgs) {
        conn.batchWithParams(statements.get(0).sql, collectArgs(statements), handler);
      } else {
        conn.batch(collectSQLs(statements), handler);
      }
    });

    return deferred.promise();
  }

  private static List<String> collectSQLs(List<Statement> statements) {
    ArrayList<String> ret = new ArrayList<>(statements.size());
    for (Statement s : statements) {
      ret.add(s.sql);
    }
    return ret;
  }

  private static List<UpdateResult> toUpdateResults(List<Integer> rowsUpdated) {
    List<UpdateResult> result = new ArrayList<>(rowsUpdated.size());
    for (Integer updated: rowsUpdated) {
      result.add(new UpdateResult(updated, null));
    }
    return result;
  }

  private static List<JsonArray> collectArgs(List<Statement> statements) {
    ArrayList<JsonArray> ret = new ArrayList<>(statements.size());
    for (Statement s : statements) {
      ret.add(s.args);
    }
    return ret;
  }

  private static boolean sameSQL(List<Statement> statements) {
    if (statements.size() == 1) {
      return true;
    }
    Iterator<Statement> iter = statements.iterator();
    String first = iter.next().sql;
    while (iter.hasNext()) {
      if (!first.equals(iter.next().sql)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allWithArgs(List<Statement> statements) {
    if (statements.size() == 1) {
      return statements.get(0).withArgs();
    }
    Iterator<Statement> iter = statements.iterator();
    boolean prepared = iter.next().withArgs();
    while (iter.hasNext()) {
      if (prepared != (iter.next().withArgs())) {
        throw new UnsupportedOperationException("All messages must be either withArgs or not.");
      }
    }
    return prepared;
  }

  @Override
  public CheckResult check() {
    CountDownLatch countDown = new CountDownLatch(1);
    AtomicReference<CheckResult> checkResult = new AtomicReference<>();

    underlying.getConnection(connection -> {
      SQLConnection conn = connection.result();
      conn.query("SELECT 1;", result -> {
        try {
          if (result.succeeded() && result.result().getNumRows() == 1) {
            checkResult.set(CheckResult.OK);
          } else {
            checkResult.set(CheckResult.failed((Exception) result.cause()));
          }
        } finally {
          conn.close();
        }
        countDown.countDown();
      });
    });
    return checkResult.get();
  }

  @Override
  public void close() throws IOException {
    underlying.close();
  }
}
