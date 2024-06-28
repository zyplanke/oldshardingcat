package io.shardingcat.manager.response;

import java.util.Map;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import io.shardingcat.manager.ManagerConnection;
import io.shardingcat.net.mysql.OkPacket;
import io.shardingcat.statistic.stat.UserStat;
import io.shardingcat.statistic.stat.UserStatAnalyzer;

public final class ReloadUserStat {
	
	private static final Logger logger = LoggerFactory.getLogger(ReloadUserStat.class);

    public static void execute(ManagerConnection c) {
    	
    	Map<String, UserStat> statMap = UserStatAnalyzer.getInstance().getUserStatMap();
        for (UserStat userStat : statMap.values()) {
        	userStat.reset();
        }
    	
        StringBuilder s = new StringBuilder();
        s.append(c).append("Reset show @@sql  @@sql.sum  @@sql.slow success by manager");
        
        logger.warn(s.toString());
        
        OkPacket ok = new OkPacket();
        ok.packetId = 1;
        ok.affectedRows = 1;
        ok.serverStatus = 2;
        ok.message = "Reset show @@sql  @@sql.sum @@sql.slow success".getBytes();
        ok.write(c);
    }

}
