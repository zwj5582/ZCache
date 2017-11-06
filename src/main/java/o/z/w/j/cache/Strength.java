/*
 * Created by ZhongWenjie on 2017-11-06 17:57.
 */

package o.z.w.j.cache;

public enum Strength {

	STRONG{
		@Override
		<K, V> ValueReference<K, V> referenceValue(LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight) {
			return new StrongValueReference<K,V>(value);
		}
	},
	SOFT{
		@Override
		<K, V> ValueReference<K, V> referenceValue(LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight) {
			return new SoftValueReference<K, V>(segment.valueReferenceQueue,value,entry);
		}
	};

	abstract <K, V> ValueReference<K, V> referenceValue(
			LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight);

}
