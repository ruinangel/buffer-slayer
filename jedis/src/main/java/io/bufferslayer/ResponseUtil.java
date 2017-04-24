package io.bufferslayer;

import org.jdeferred.DoneFilter;
import org.jdeferred.Promise;

/**
 * Created by guohang.bao on 2017/4/25.
 */
final class ResponseUtil {

  @SuppressWarnings("unchecked")
  static <T> Promise transformResponse(Promise promise, final Builder<T> builder) {
    return promise.then(new DoneFilter<Object, T>() {
      @Override
      public T filterDone(Object result) {
        return builder.build(result);
      }
    });
  }
}
