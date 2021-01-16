/*
 * Created by ZhongWenjie on 2017-11-03 13:43.
 */

package o.z.w.j.cache;

public class StrongEntry<K,V> implements ReferenceEntry<K,V>{

	final K key;

	final int hash;

	ReferenceEntry<K, V> next;

	long time;

	volatile ValueReference<K, V> valueReference;

	StrongEntry(K key , int hash , ReferenceEntry<K,V> next){
		this.key = key;
		this.hash = hash;
		this.next = next;
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public ValueReference<K,V> getValue() {
		return valueReference;
	}

	@Override
	public void setValue(ValueReference<K,V> value) {
		this.valueReference = value;
	}

	@Override
	public void setTime(long time) {
		this.time=time;
	}

	@Override
	public long getTime() {
		return this.time;
	}

	@Override
	public ReferenceEntry<K, V> next() {
		return this.next;
	}

	@Override
	public void setNext(ReferenceEntry<K, V> entry) {
		this.next=entry;
	}
}
