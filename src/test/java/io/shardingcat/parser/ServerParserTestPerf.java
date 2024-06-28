
package io.shardingcat.parser;

import io.shardingcat.server.parser.ServerParseSet;

/**
 * @author shardingcat
 */
public final class ServerParserTestPerf {

    private static void parseSetPerf() {
        // ServerParse.parse("show databases");
        // ServerParseSet.parse("set autocommit=1");
        // ServerParseSet.parse("set names=1");
        ServerParseSet.parse("SET character_set_results = NULL", 4);
        // ServerParse.parse("select id,name,value from t");
        // ServerParse.parse("select * from offer where member_id='abc'");
    }

    public static void main(String[] args) {
        parseSetPerf();
        int count = 10000000;

        System.currentTimeMillis();
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            parseSetPerf();
        }
        long t2 = System.currentTimeMillis();

        // print time
        System.out.println("take:" + ((t2 - t1) * 1000 * 1000) / count + " ns.");
    }

}