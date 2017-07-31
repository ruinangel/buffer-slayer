package io.github.tramchamploo.bufferslayer;

import io.reactivex.functions.Consumer;
import java.util.List;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SenderConsumerBridge {

  private static final Logger logger = LoggerFactory.getLogger(SenderConsumerBridge.class);

  /**
   * Adapt a {@link Sender} to rx-java's {@link Consumer}
   */
  static <M extends Message, R> Consumer<List<M>> toConsumer(final Sender<M, R> sender) {
    return new Consumer<List<M>>() {
      @Override
      public void accept(final List<M> messages) throws Exception {
        if (messages.isEmpty()) return;
        logger.debug("Sending {} messages.", messages.size());

        if (sender instanceof SyncSender) {
          SyncSender<M, R> syncSender = (SyncSender<M, R>) sender;

          try {
            List<R> result = syncSender.send(messages);
            DeferredHolder.batchResolve(messages, result);
          } catch (Throwable t) {
            DeferredHolder.batchReject(messages, MessageDroppedException.dropped(t, messages));
          }
        } else {
          AsyncSender<M, R> asyncSender = (AsyncSender<M, R>) sender;

          asyncSender.send(messages).done(new DoneCallback<List<R>>() {
            @Override
            public void onDone(List<R> result) {
              DeferredHolder.batchResolve(messages, result);
            }
          }).fail(new FailCallback<MessageDroppedException>() {
            @Override
            public void onFail(MessageDroppedException result) {
              DeferredHolder.batchReject(messages, result);
            }
          });
        }
      }
    };
  }
}
