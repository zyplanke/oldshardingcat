package io.shardingcat.memory;


import com.google.common.annotations.VisibleForTesting;
import io.shardingcat.config.model.SystemConfig;
import io.shardingcat.memory.unsafe.Platform;
import io.shardingcat.memory.unsafe.memory.mm.MemoryManager;
import io.shardingcat.memory.unsafe.memory.mm.ResultMergeMemoryManager;
import io.shardingcat.memory.unsafe.storage.DataNodeDiskManager;
import io.shardingcat.memory.unsafe.storage.SerializerManager;
import io.shardingcat.memory.unsafe.utils.JavaUtils;
import io.shardingcat.memory.unsafe.utils.ShardingCatPropertyConf;
import org.apache.log4j.Logger;

/**
 * Created by zagnix on 2016/6/2.
 * ShardingCat内存管理工具类
 * 规划为三部分内存:结果集处理内存,系统预留内存,网络处理内存
 * 其中网络处理内存部分全部为Direct Memory
 * 结果集内存分为Direct Memory 和 Heap Memory，但目前仅使用Direct Memory
 * 系统预留内存为 Heap Memory。
 * 系统运行时，必须设置-XX:MaxDirectMemorySize 和 -Xmx JVM参数
 * -Xmx1024m -Xmn512m -XX:MaxDirectMemorySize=2048m -Xss256K -XX:+UseParallelGC
 */

public class ShardingCatMemory {
	private static Logger LOGGER = Logger.getLogger(ShardingCatMemory.class);

	public final  static double DIRECT_SAFETY_FRACTION  = 0.7;
	private final long systemReserveBufferSize;

	private final long memoryPageSize;
	private final long spillsFileBufferSize;
	private final long resultSetBufferSize;
	private final int numCores;


	/**
	 * 内存管理相关关键类
	 */
	private final ShardingCatPropertyConf conf;
	private final MemoryManager resultMergeMemoryManager;
	private final DataNodeDiskManager blockManager;
	private final SerializerManager serializerManager;
	private final SystemConfig system;


	public ShardingCatMemory(SystemConfig system,long totalNetWorkBufferSize) throws NoSuchFieldException, IllegalAccessException {

		this.system = system;

		LOGGER.info("useOffHeapForMerge = " + system.getUseOffHeapForMerge());
		LOGGER.info("memoryPageSize = " + system.getMemoryPageSize());
		LOGGER.info("spillsFileBufferSize = " + system.getSpillsFileBufferSize());
		LOGGER.info("useStreamOutput = " + system.getUseStreamOutput());
		LOGGER.info("systemReserveMemorySize = " + system.getSystemReserveMemorySize());
		LOGGER.info("totalNetWorkBufferSize = " + JavaUtils.bytesToString2(totalNetWorkBufferSize));
		LOGGER.info("dataNodeSortedTempDir = " + system.getDataNodeSortedTempDir());

		this.conf = new ShardingCatPropertyConf();
		numCores = Runtime.getRuntime().availableProcessors();

		this.systemReserveBufferSize = JavaUtils.
				byteStringAsBytes(system.getSystemReserveMemorySize());
		this.memoryPageSize = JavaUtils.
				byteStringAsBytes(system.getMemoryPageSize());

		this.spillsFileBufferSize = JavaUtils.
				byteStringAsBytes(system.getSpillsFileBufferSize());

		/**
		 * 目前merge，order by ，limit 没有使用On Heap内存
		 */
		long maxOnHeapMemory =  (Platform.getMaxHeapMemory()-systemReserveBufferSize);

		assert maxOnHeapMemory > 0;

		resultSetBufferSize =
				(long)((Platform.getMaxDirectMemory()-2*totalNetWorkBufferSize)*DIRECT_SAFETY_FRACTION);

		assert resultSetBufferSize > 0;

		/**
		 * shardingcat.merge.memory.offHeap.enabled
		 * shardingcat.buffer.pageSize
		 * shardingcat.memory.offHeap.size
		 * shardingcat.merge.file.buffer
		 * shardingcat.direct.output.result
		 * shardingcat.local.dir
		 */

		if(system.getUseOffHeapForMerge()== 1){
			conf.set("shardingcat.memory.offHeap.enabled","true");
		}else{
			conf.set("shardingcat.memory.offHeap.enabled","false");
		}

		if(system.getUseStreamOutput() == 1){
			conf.set("shardingcat.stream.output.result","true");
		}else{
			conf.set("shardingcat.stream.output.result","false");
		}


		if(system.getMemoryPageSize() != null){
			conf.set("shardingcat.buffer.pageSize",system.getMemoryPageSize());
		}else{
			conf.set("shardingcat.buffer.pageSize","32k");
		}


		if(system.getSpillsFileBufferSize() != null){
			conf.set("shardingcat.merge.file.buffer",system.getSpillsFileBufferSize());
		}else{
			conf.set("shardingcat.merge.file.buffer","32k");
		}

		conf.set("shardingcat.pointer.array.len","1k")
			.set("shardingcat.memory.offHeap.size", JavaUtils.bytesToString2(resultSetBufferSize));

		LOGGER.info("shardingcat.memory.offHeap.size: " +
				JavaUtils.bytesToString2(resultSetBufferSize));

		resultMergeMemoryManager =
				new ResultMergeMemoryManager(conf,numCores,maxOnHeapMemory);


		serializerManager = new SerializerManager();

		blockManager = new DataNodeDiskManager(conf,true,serializerManager);

	}


	@VisibleForTesting
	public ShardingCatMemory() throws NoSuchFieldException, IllegalAccessException {
		this.system = null;
		this.systemReserveBufferSize = 0;
		this.memoryPageSize = 0;
		this.spillsFileBufferSize = 0;
		conf = new ShardingCatPropertyConf();
		numCores = Runtime.getRuntime().availableProcessors();

		long maxOnHeapMemory =  (Platform.getMaxHeapMemory());
		assert maxOnHeapMemory > 0;

		resultSetBufferSize = (long)((Platform.getMaxDirectMemory())*DIRECT_SAFETY_FRACTION);

		assert resultSetBufferSize > 0;
		/**
		 * shardingcat.memory.offHeap.enabled
		 * shardingcat.buffer.pageSize
		 * shardingcat.memory.offHeap.size
		 * shardingcat.testing.memory
		 * shardingcat.merge.file.buffer
		 * shardingcat.direct.output.result
		 * shardingcat.local.dir
		 */
		conf.set("shardingcat.memory.offHeap.enabled","true")
				.set("shardingcat.pointer.array.len","8K")
				.set("shardingcat.buffer.pageSize","1m")
				.set("shardingcat.memory.offHeap.size", JavaUtils.bytesToString2(resultSetBufferSize))
				.set("shardingcat.stream.output.result","false");

		LOGGER.info("shardingcat.memory.offHeap.size: " + JavaUtils.bytesToString2(resultSetBufferSize));

		resultMergeMemoryManager =
				new ResultMergeMemoryManager(conf,numCores,maxOnHeapMemory);

		serializerManager = new SerializerManager();

		blockManager = new DataNodeDiskManager(conf,true,serializerManager);

	}

		public ShardingCatPropertyConf getConf() {
		return conf;
	}

	public long getResultSetBufferSize() {
		return resultSetBufferSize;
	}

	public MemoryManager getResultMergeMemoryManager() {
		return resultMergeMemoryManager;
	}

	public SerializerManager getSerializerManager() {
		return serializerManager;
	}

	public DataNodeDiskManager getBlockManager() {
		return blockManager;
	}

}
