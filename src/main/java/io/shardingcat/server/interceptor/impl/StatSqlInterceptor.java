package io.shardingcat.server.interceptor.impl;

import io.shardingcat.server.interceptor.SQLInterceptor;

public class StatSqlInterceptor implements SQLInterceptor {

	@Override
	public String interceptSQL(String sql, int sqlType) {
		// TODO Auto-generated method stub
		final int atype = sqlType;
        final String sqls = DefaultSqlInterceptor.processEscape(sql);
        return sql;
	}

}
