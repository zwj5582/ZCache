/*
 * Created by ZhongWenjie on 2017-11-06 17:39.
 */

package o.z.w.j.cache;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

public final class SoftValueReference<K,V> extends SoftReference<V> implements ValueReference<K,V>  {

	final ReferenceEntry<K, V> entry;

	public SoftValueReference(V referent, ReferenceEntry<K,V> entry) {
		super(referent);
		this.entry = entry;
	}

	public SoftValueReference(ReferenceQueue<? super V> queue, V referent, ReferenceEntry<K,V> entry) {
		super(referent, queue);
		this.entry = entry;
	}

	@Override
	public ValueReference<K, V> copyFor(ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
		return new SoftValueReference<K, V>(queue,value,entry);
	}
}
