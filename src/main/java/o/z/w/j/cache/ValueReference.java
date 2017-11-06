/*
 * Created by ZhongWenjie on 2017-11-03 11:45.
 */

package o.z.w.j.cache;

import java.lang.ref.ReferenceQueue;

public interface ValueReference<K,V> {

	V get();

	ValueReference<K, V> copyFor(
			ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry);

}
