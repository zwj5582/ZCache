/*
 * Created by ZhongWenjie on 2017-11-06 14:25.
 */

package o.z.w.j.cache;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.*;

public class LoadingValueReference<K,V> implements ValueReference<K,V> {

	private FutureTask<V> future;

	public LoadingValueReference(final CacheLoader<K,V> loader, final K key){

		this.future = new FutureTask<V>(new Callable<V>() {
			@Override
			public V call() throws Exception {
				return loader.load(key);
			}
		});
		Executors.newCachedThreadPool().submit(future);
	}

	@Override
	public V get() {
		try {
			return future.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public ValueReference<K, V> copyFor(ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
		return null;
	}
}
