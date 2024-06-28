
package io.shardingcat.net.handler;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.shardingcat.ShardingCatServer;
import io.shardingcat.backend.mysql.SecurityUtil;
import io.shardingcat.config.Capabilities;
import io.shardingcat.config.ErrorCode;
import io.shardingcat.config.model.UserConfig;
import io.shardingcat.net.FrontendConnection;
import io.shardingcat.net.NIOHandler;
import io.shardingcat.net.NIOProcessor;
import io.shardingcat.net.mysql.AuthPacket;
import io.shardingcat.net.mysql.MySQLPacket;
import io.shardingcat.net.mysql.QuitPacket;

/**
 * 前端认证处理器
 * 
 * @author shardingcat
 */
public class FrontendAuthenticator implements NIOHandler {
	
    private static final Logger LOGGER = LoggerFactory.getLogger(FrontendAuthenticator.class);
    private static final byte[] AUTH_OK = new byte[] { 7, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0 };
    
    protected final FrontendConnection source;

    public FrontendAuthenticator(FrontendConnection source) {
        this.source = source;
    }

    @Override
    public void handle(byte[] data) {
        // check quit packet
        if (data.length == QuitPacket.QUIT.length && data[4] == MySQLPacket.COM_QUIT) {
            source.close("quit packet");
            return;
        }

        AuthPacket auth = new AuthPacket();
        auth.read(data);

        //huangyiming add
        int nopassWordLogin = ShardingCatServer.getInstance().getConfig().getSystem().getNonePasswordLogin();
        //如果无密码登陆则跳过密码验证这个步骤
        boolean skipPassWord = false;
        String defaultUser = "";
        if(nopassWordLogin == 1){
        	skipPassWord = true;
        	Map<String, UserConfig> userMaps =  ShardingCatServer.getInstance().getConfig().getUsers();
        	if(!userMaps.isEmpty()){
        		setDefaultAccount(auth, userMaps);
        	}
        }
        // check user
        if (!checkUser(auth.user, source.getHost())) {
        	failure(ErrorCode.ER_ACCESS_DENIED_ERROR, "Access denied for user '" + auth.user + "' with host '" + source.getHost()+ "'");
        	return;
        }
        // check password
        if (!skipPassWord && !checkPassword(auth.password, auth.user)) {
        	failure(ErrorCode.ER_ACCESS_DENIED_ERROR, "Access denied for user '" + auth.user + "', because password is error ");
        	return;
        }
        
        // check degrade
        if ( isDegrade( auth.user ) ) {
        	 failure(ErrorCode.ER_ACCESS_DENIED_ERROR, "Access denied for user '" + auth.user + "', because service be degraded ");
             return;
        }
        
        // check schema
        switch (checkSchema(auth.database, auth.user)) {
        case ErrorCode.ER_BAD_DB_ERROR:
            failure(ErrorCode.ER_BAD_DB_ERROR, "Unknown database '" + auth.database + "'");
            break;
        case ErrorCode.ER_DBACCESS_DENIED_ERROR:
            String s = "Access denied for user '" + auth.user + "' to database '" + auth.database + "'";
            failure(ErrorCode.ER_DBACCESS_DENIED_ERROR, s);
            break;
        default:
            success(auth);
        }
    }

    /**
     * 设置了无密码登陆的情况下把客户端传过来的用户账号改变为默认账户
     * @param auth
     * @param userMaps
     */
	private void setDefaultAccount(AuthPacket auth, Map<String, UserConfig> userMaps) {
		String defaultUser;
		Iterator<UserConfig> items = userMaps.values().iterator();
		while(items.hasNext()){
			UserConfig userConfig = items.next();
			if(userConfig.isDefaultAccount()){
				defaultUser = userConfig.getName(); 
				auth.user = defaultUser;
			}
		}
	}
    
    //TODO: add by zhuam
    //前端 connection 达到该用户设定的阀值后, 立马降级拒绝连接
    protected boolean isDegrade(String user) {
    	
    	int benchmark = source.getPrivileges().getBenchmark(user);
    	if ( benchmark > 0 ) {
    	
	    	int forntedsLength = 0;
	    	NIOProcessor[] processors = ShardingCatServer.getInstance().getProcessors();
			for (NIOProcessor p : processors) {
				forntedsLength += p.getForntedsLength();
			}
		
			if ( forntedsLength >= benchmark ) {							
				return true;
			}			
    	}
		
		return false;
    }
    
    protected boolean checkUser(String user, String host) {
        return source.getPrivileges().userExists(user, host);
    }

    protected boolean checkPassword(byte[] password, String user) {
        String pass = source.getPrivileges().getPassword(user);

        // check null
        if (pass == null || pass.length() == 0) {
            if (password == null || password.length == 0) {
                return true;
            } else {
                return false;
            }
        }
        if (password == null || password.length == 0) {
            return false;
        }

        // encrypt
        byte[] encryptPass = null;
        try {
            encryptPass = SecurityUtil.scramble411(pass.getBytes(), source.getSeed());
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warn(source.toString(), e);
            return false;
        }
        if (encryptPass != null && (encryptPass.length == password.length)) {
            int i = encryptPass.length;
            while (i-- != 0) {
                if (encryptPass[i] != password[i]) {
                    return false;
                }
            }
        } else {
            return false;
        }

        return true;
    }

    protected int checkSchema(String schema, String user) {
        if (schema == null) {
            return 0;
        }
        FrontendPrivileges privileges = source.getPrivileges();
        if (!privileges.schemaExists(schema)) {
            return ErrorCode.ER_BAD_DB_ERROR;
        }
        Set<String> schemas = privileges.getUserSchemas(user);
        if (schemas == null || schemas.size() == 0 || schemas.contains(schema)) {
            return 0;
        } else {
            return ErrorCode.ER_DBACCESS_DENIED_ERROR;
        }
    }

    protected void success(AuthPacket auth) {
        source.setAuthenticated(true);
        source.setUser(auth.user);
        source.setSchema(auth.database);
        source.setCharsetIndex(auth.charsetIndex);
        source.setHandler(new FrontendCommandHandler(source));

        if (LOGGER.isInfoEnabled()) {
            StringBuilder s = new StringBuilder();
            s.append(source).append('\'').append(auth.user).append("' login success");
            byte[] extra = auth.extra;
            if (extra != null && extra.length > 0) {
                s.append(",extra:").append(new String(extra));
            }
            LOGGER.info(s.toString());
        }

        ByteBuffer buffer = source.allocate();
        source.write(source.writeToBuffer(AUTH_OK, buffer));
        boolean clientCompress = Capabilities.CLIENT_COMPRESS==(Capabilities.CLIENT_COMPRESS & auth.clientFlags);
        boolean usingCompress= ShardingCatServer.getInstance().getConfig().getSystem().getUseCompression()==1 ;
        if(clientCompress&&usingCompress)
        {
            source.setSupportCompress(true);
        }
    }

    protected void failure(int errno, String info) {
        LOGGER.error(source.toString() + info);
        source.writeErrMessage((byte) 2, errno, info);
    }

}