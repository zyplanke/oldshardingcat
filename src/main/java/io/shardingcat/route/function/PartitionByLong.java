
package io.shardingcat.route.function;

import io.shardingcat.config.model.rule.RuleAlgorithm;
import io.shardingcat.route.util.PartitionUtil;

public final class PartitionByLong extends AbstractPartitionAlgorithm implements RuleAlgorithm {
	protected int[] count;
	protected int[] length;
	protected PartitionUtil partitionUtil;

	private static int[] toIntArray(String string) {
		String[] strs = io.shardingcat.util.SplitUtil.split(string, ',', true);
		int[] ints = new int[strs.length];
		for (int i = 0; i < strs.length; ++i) {
			ints[i] = Integer.parseInt(strs[i]);
		}
		return ints;
	}

	public void setPartitionCount(String partitionCount) {
		this.count = toIntArray(partitionCount);
	}

	public void setPartitionLength(String partitionLength) {
		this.length = toIntArray(partitionLength);
	}

	@Override
	public void init() {
		partitionUtil = new PartitionUtil(count, length);

	}

	@Override
	public Integer calculate(String columnValue)  {
//		columnValue = NumberParseUtil.eliminateQoute(columnValue);
		try {
			long key = Long.parseLong(columnValue);
			return partitionUtil.partition(key);
		} catch (NumberFormatException e){
			throw new IllegalArgumentException(new StringBuilder().append("columnValue:").append(columnValue).append(" Please eliminate any quote and non number within it.").toString(),e);
		}
	}
	
	@Override
	public Integer[] calculateRange(String beginValue, String endValue)  {
		return AbstractPartitionAlgorithm.calculateSequenceRange(this, beginValue, endValue);
	}

//	@Override
//	public int getPartitionCount() {
//		int nPartition = 0;
//		for(int i = 0; i < count.length; i++) {
//			nPartition += count[i];
//		}
//		return nPartition;
//	}
	
}