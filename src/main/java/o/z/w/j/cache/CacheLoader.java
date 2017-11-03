/*
 * Created by ZhongWenjie on 2017-11-03 10:43.
 */

package o.z.w.j.cache;

public interface CacheLoader<K,V> {

	V load(K key);

}
