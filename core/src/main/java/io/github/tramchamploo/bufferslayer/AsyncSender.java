package io.github.tramchamploo.bufferslayer;

import java.util.List;
import org.jdeferred.Promise;

/**
 * Sender that send messages in async way and return a promise
 */
public interface AsyncSender<M extends Message, R> extends Sender<M, R> {

  /** The name of the system property for setting the thread priority for this Scheduler. */
  String KEY_IO_PRIORITY = "bufferslayer.io-priority";

  /**
   * asynchronously send messages
   *
   * @param messages messages to send
   * @return promise represents the result of sending
   */
  Promise<List<R>, MessageDroppedException, ?> send(List<M> messages);
}
