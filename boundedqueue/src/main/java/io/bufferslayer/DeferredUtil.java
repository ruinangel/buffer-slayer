package io.bufferslayer;

import java.util.ArrayList;
import java.util.List;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneResult;

/**
 * Created by guohang.bao on 2017/4/14.
 */
class DeferredUtil {

  static List<Object> toResults(MultipleResults mr) {
    ArrayList<Object> result = new ArrayList<>();
    for (OneResult next : mr) {
      List<Object> batchResult = (List<Object>) next.getResult();
      result.addAll(batchResult);
    }
    return result;
  }
}
