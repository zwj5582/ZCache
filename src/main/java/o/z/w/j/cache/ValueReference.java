/*
 * Created by ZhongWenjie on 2017-11-03 11:45.
 */

package o.z.w.j.cache;

public interface ValueReference<K,V> {

	V get(K key);

}
