/*
 * Created by ZhongWenjie on 2017-11-06 14:16.
 */

package o.z.w.j.cache;

import java.lang.ref.ReferenceQueue;

public class StrongValueReference<K,V> implements ValueReference<K,V> {

	final V value;

	public StrongValueReference(V value) {
		this.value = value;
	}

	@Override
	public V get() {
		return value;
	}

	@Override
	public ValueReference<K, V> copyFor(ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
		return this;
	}
}
