package io.github.tramchamploo.bufferslayer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

public class BatchMySQLClient implements MySQLClient {

  private final AsyncSQLClient underlying;
  private final Reporter<Statement, UpdateResult> reporter;

  private BatchMySQLClient(AsyncSQLClient underlying, Reporter<Statement, UpdateResult> reporter) {
    this.underlying = underlying;
    this.reporter = reporter;
  }

  // visible for tests
  static AsyncSQLClient wrap(AsyncSQLClient client, Reporter<Statement, UpdateResult> reporter) {
    return new BatchMySQLClient(client, reporter);
  }

  @SuppressWarnings("unchecked")
  public static AsyncSQLClient wrap(AsyncSQLClient client, ReporterProperties properties) {
    return new BatchMySQLClient(client, properties.setSender(new SQLConnectionSender(client)).toBuilder().build());
  }

  @Override
  public SQLClient getConnection(Handler<AsyncResult<SQLConnection>> handler) {
    return underlying.getConnection(result -> {
      if (result.succeeded()) {
        try (SQLConnection realConnection = result.result()) {
          handler.handle(Future.succeededFuture(BatchSQLConnection.wrap(realConnection, reporter)));
        }
      } else {
        handler.handle(Future.failedFuture(result.cause()));
      }
    });
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    underlying.close(completionHandler);
  }

  @Override
  public void close() {
    underlying.close();
  }
}
