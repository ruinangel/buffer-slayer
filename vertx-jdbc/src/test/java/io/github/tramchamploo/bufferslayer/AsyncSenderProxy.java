package io.github.tramchamploo.bufferslayer;

import io.vertx.ext.sql.UpdateResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jdeferred.Promise;

/**
 * Delegate sending and trigger onMessages afterwards
 */
public class AsyncSenderProxy implements AsyncSender<Statement, UpdateResult> {

  private AtomicBoolean closed = new AtomicBoolean(false);
  private Consumer<List<UpdateResult>> onMessages = messages -> { };
  final AsyncSender<Statement, UpdateResult> delegate;

  public AsyncSenderProxy(AsyncSender<Statement, UpdateResult> delegate) {
    this.delegate = delegate;
  }

  @Override
  public CheckResult check() {
    return CheckResult.OK;
  }

  @Override
  public void close() {
    closed.set(true);
  }

  @Override
  public Promise<List<UpdateResult>, MessageDroppedException, ?> send(List<Statement> messages) {
    if (closed.get()) {
      throw new IllegalStateException("Closed!");
    }
    Promise<List<UpdateResult>, MessageDroppedException, ?> promise = delegate.send(messages);
    promise.done(result -> onMessages.accept(result));
    return promise;
  }

  public void onMessages(Consumer<List<UpdateResult>> onMessages) {
    this.onMessages = onMessages;
  }
}
