/*
 * Created by ZhongWenjie on 2017-11-03 11:54.
 */

package o.z.w.j.cache;

public interface ReferenceEntry<K,V> {

	K getKey();

	V getValue();

	void setValue(V value);

	void setTime(long time);

	long getTime();

	ReferenceEntry<K,V> next();

	void setNext(ReferenceEntry<K,V> entry);

}
