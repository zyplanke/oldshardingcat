package io.shardingcat.route.impl;

import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import io.shardingcat.ShardingCatServer;
import io.shardingcat.cache.LayerCachePool;
import io.shardingcat.config.model.SchemaConfig;
import io.shardingcat.config.model.SystemConfig;
import io.shardingcat.route.RouteResultset;
import io.shardingcat.route.RouteStrategy;
import io.shardingcat.route.util.RouterUtil;
import io.shardingcat.server.ServerConnection;
import io.shardingcat.server.parser.ServerParse;
import io.shardingcat.sqlengine.mpp.LoadData;

public abstract class AbstractRouteStrategy implements RouteStrategy {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRouteStrategy.class);

	@Override
	public RouteResultset route(SystemConfig sysConfig, SchemaConfig schema, int sqlType, String origSQL,
			String charset, ServerConnection sc, LayerCachePool cachePool) throws SQLNonTransientException {

		//对应schema标签checkSQLschema属性，把表示schema的字符去掉
		if (schema.isCheckSQLSchema()) {
			origSQL = RouterUtil.removeSchema(origSQL, schema.getName());
		}

		/**
     * 处理一些路由之前的逻辑
     * 全局序列号，父子表插入
     */
		if ( beforeRouteProcess(schema, sqlType, origSQL, sc) ) {
			return null;
		}

		/**
		 * SQL 语句拦截
		 */
		String stmt = ShardingCatServer.getInstance().getSqlInterceptor().interceptSQL(origSQL, sqlType);
		if (!origSQL.equals(stmt) && LOGGER.isDebugEnabled()) {
			LOGGER.debug("sql intercepted to " + stmt + " from " + origSQL);
		}


		RouteResultset rrs = new RouteResultset(stmt, sqlType);

		/**
		 * 优化debug loaddata输出cache的日志会极大降低性能
		 */
		if (LOGGER.isDebugEnabled() && origSQL.startsWith(LoadData.loadDataHint)) {
			rrs.setCacheAble(false);
		}

        /**
         * rrs携带ServerConnection的autocommit状态用于在sql解析的时候遇到
         * select ... for update的时候动态设定RouteResultsetNode的canRunInReadDB属性
         */
		if (sc != null ) {
			rrs.setAutocommit(sc.isAutocommit());
		}

		/**
		 * DDL 语句的路由
		 */
		if (ServerParse.DDL == sqlType) {
			return RouterUtil.routeToDDLNode(rrs, sqlType, stmt, schema);
		}

		/**
		 * 检查是否有分片
		 */
		if (schema.isNoSharding() && ServerParse.SHOW != sqlType) {
			rrs = RouterUtil.routeToSingleNode(rrs, schema.getDataNode(), stmt);
		} else {
			RouteResultset returnedSet = routeSystemInfo(schema, sqlType, stmt, rrs);
			if (returnedSet == null) {
				rrs = routeNormalSqlWithAST(schema, stmt, rrs, charset, cachePool,sqlType,sc);
			}
		}

		return rrs;
	}

	/**
	 * 路由之前必要的处理
	 * 主要是全局序列号插入，还有子表插入
	 */
	private boolean beforeRouteProcess(SchemaConfig schema, int sqlType, String origSQL, ServerConnection sc)
			throws SQLNonTransientException {
		
		return RouterUtil.processWithShardingCatSeq(schema, sqlType, origSQL, sc)
				|| (sqlType == ServerParse.INSERT && RouterUtil.processERChildTable(schema, origSQL, sc))
				|| (sqlType == ServerParse.INSERT && RouterUtil.processInsert(schema, sqlType, origSQL, sc));
	}

	/**
	 * 通过解析AST语法树类来寻找路由
	 */
	public abstract RouteResultset routeNormalSqlWithAST(SchemaConfig schema, String stmt, RouteResultset rrs,
			String charset, LayerCachePool cachePool,int sqlType,ServerConnection sc) throws SQLNonTransientException;

	/**
	 * 路由信息指令, 如 SHOW、SELECT@@、DESCRIBE
	 */
	public abstract RouteResultset routeSystemInfo(SchemaConfig schema, int sqlType, String stmt, RouteResultset rrs)
			throws SQLSyntaxErrorException;

	/**
	 * 解析 Show 之类的语句
	 */
	public abstract RouteResultset analyseShowSQL(SchemaConfig schema, RouteResultset rrs, String stmt)
			throws SQLNonTransientException;

}
