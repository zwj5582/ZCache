/*
 * Created by ZhongWenjie on 2017-11-06 14:16.
 */

package o.z.w.j.cache;

public class StrongValueReference<K,V> implements ValueReference<K,V> {

	final V value;

	public StrongValueReference(V value) {
		this.value = value;
	}

	@Override
	public V get(K key) {
		return value;
	}
}
