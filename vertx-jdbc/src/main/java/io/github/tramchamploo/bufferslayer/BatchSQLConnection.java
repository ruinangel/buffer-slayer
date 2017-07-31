package io.github.tramchamploo.bufferslayer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLRowStream;
import io.vertx.ext.sql.TransactionIsolation;
import io.vertx.ext.sql.UpdateResult;
import java.util.List;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;

/**
 * Convert SQLConnection's update to batch
 */
public class BatchSQLConnection implements SQLConnection {

  private final SQLConnection delegate;
  private final Reporter<Statement, UpdateResult> reporter;

  private BatchSQLConnection(SQLConnection delegate, Reporter<Statement, UpdateResult> reporter) {
    this.delegate = delegate;
    this.reporter = reporter;
  }

  public static SQLConnection wrap(SQLConnection connection, Reporter<Statement, UpdateResult> reporter) {
    return new BatchSQLConnection(connection, reporter);
  }

  @Override
  public SQLConnection setAutoCommit(boolean autoCommit, Handler<AsyncResult<Void>> resultHandler) {
    return delegate.setAutoCommit(autoCommit, resultHandler);
  }

  @Override
  public SQLConnection execute(String sql, Handler<AsyncResult<Void>> resultHandler) {
    return delegate.execute(sql, resultHandler);
  }

  @Override
  public SQLConnection query(String sql, Handler<AsyncResult<ResultSet>> resultHandler) {
    return delegate.query(sql, resultHandler);
  }

  @Override
  public SQLConnection queryStream(String sql, Handler<AsyncResult<SQLRowStream>> handler) {
    return delegate.queryStream(sql, handler);
  }

  @Override
  public SQLConnection queryWithParams(String sql, JsonArray params, Handler<AsyncResult<ResultSet>> resultHandler) {
    return delegate.queryWithParams(sql, params, resultHandler);
  }

  @Override
  public SQLConnection queryStreamWithParams(String sql, JsonArray params, Handler<AsyncResult<SQLRowStream>> handler) {
    return delegate.queryStreamWithParams(sql, params, handler);
  }

  @Override
  public SQLConnection update(String sql, Handler<AsyncResult<UpdateResult>> resultHandler) {
    Promise<UpdateResult, MessageDroppedException, ?> promise = reporter.report(Statement.builder().sql(sql).build());
    triggerHandler(promise, resultHandler);
    return this;
  }

  @Override
  public SQLConnection updateWithParams(String sql, JsonArray params, Handler<AsyncResult<UpdateResult>> resultHandler) {
    Promise<UpdateResult, MessageDroppedException, ?> promise = reporter.report(Statement.builder().sql(sql).args(params).build());
    triggerHandler(promise, resultHandler);
    return this;
  }

  private void triggerHandler(Promise<UpdateResult, MessageDroppedException, ?> promise, Handler<AsyncResult<UpdateResult>> resultHandler) {
    promise.done((DoneCallback<UpdateResult>) result -> resultHandler.handle(Future.succeededFuture(result)))
        .fail((FailCallback<MessageDroppedException>) ex -> resultHandler.handle(Future.failedFuture(ex)));
  }

  @Override
  public SQLConnection call(String sql, Handler<AsyncResult<ResultSet>> resultHandler) {
    return delegate.call(sql, resultHandler);
  }

  @Override
  public SQLConnection callWithParams(String sql, JsonArray params, JsonArray outputs, Handler<AsyncResult<ResultSet>> resultHandler) {
    return delegate.callWithParams(sql, params, outputs, resultHandler);
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    delegate.close(handler);
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public SQLConnection commit(Handler<AsyncResult<Void>> handler) {
    return delegate.commit(handler);
  }

  @Override
  public SQLConnection rollback(Handler<AsyncResult<Void>> handler) {
    return delegate.rollback(handler);
  }

  @Override
  public SQLConnection setQueryTimeout(int timeoutInSeconds) {
    return delegate.setQueryTimeout(timeoutInSeconds);
  }

  @Override
  public SQLConnection batch(List<String> sqlStatements, Handler<AsyncResult<List<Integer>>> handler) {
    return delegate.batch(sqlStatements, handler);
  }

  @Override
  public SQLConnection batchWithParams(String sqlStatement, List<JsonArray> args, Handler<AsyncResult<List<Integer>>> handler) {
    return delegate.batchWithParams(sqlStatement, args, handler);
  }

  @Override
  public SQLConnection batchCallableWithParams(String sqlStatement, List<JsonArray> inArgs, List<JsonArray> outArgs, Handler<AsyncResult<List<Integer>>> handler) {
    return delegate.batchCallableWithParams(sqlStatement, inArgs, outArgs, handler);
  }

  @Override
  public SQLConnection setTransactionIsolation(TransactionIsolation isolation, Handler<AsyncResult<Void>> handler) {
    return delegate.setTransactionIsolation(isolation, handler);
  }

  @Override
  public SQLConnection getTransactionIsolation(Handler<AsyncResult<TransactionIsolation>> handler) {
    return delegate.getTransactionIsolation(handler);
  }

  @Override
  public <N> N unwrap() {
    return delegate.unwrap();
  }
}
