
package io.shardingcat.config.loader.xml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.shardingcat.config.Versions;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.alibaba.druid.wall.WallConfig;

import io.shardingcat.config.model.ClusterConfig;
import io.shardingcat.config.model.FirewallConfig;
import io.shardingcat.config.model.SystemConfig;
import io.shardingcat.config.model.UserConfig;
import io.shardingcat.config.model.UserPrivilegesConfig;
import io.shardingcat.config.util.ConfigException;
import io.shardingcat.config.util.ConfigUtil;
import io.shardingcat.config.util.ParameterMapping;
import io.shardingcat.util.DecryptUtil;
import io.shardingcat.util.SplitUtil;

/**
 * @author shardingcat
 */
@SuppressWarnings("unchecked")
public class XMLServerLoader {
    private final SystemConfig system;
    private final Map<String, UserConfig> users;
    private final FirewallConfig firewall;
    private ClusterConfig cluster;

    public XMLServerLoader() {
        this.system = new SystemConfig();
        this.users = new HashMap<String, UserConfig>();
        this.firewall = new FirewallConfig();
        this.load();
    }

    public SystemConfig getSystem() {
        return system;
    }

    public Map<String, UserConfig> getUsers() {
        return (Map<String, UserConfig>) (users.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(users));
    }

    public FirewallConfig getFirewall() {
        return firewall;
    }

    public ClusterConfig getCluster() {
        return cluster;
    }

    private void load() {
        //读取server.xml配置
        InputStream dtd = null;
        InputStream xml = null;
        try {
            dtd = XMLServerLoader.class.getResourceAsStream("/server.dtd");
            xml = XMLServerLoader.class.getResourceAsStream("/server.xml");
            Element root = ConfigUtil.getDocument(dtd, xml).getDocumentElement();

            //加载System标签
            loadSystem(root);

            //加载User标签
            loadUsers(root);

            //加载集群配置
            this.cluster = new ClusterConfig(root, system.getServerPort());

            //加载全局SQL防火墙
            loadFirewall(root);
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException(e);
        } finally {
            if (dtd != null) {
                try {
                    dtd.close();
                } catch (IOException e) {
                }
            }
            if (xml != null) {
                try {
                    xml.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * 初始载入配置获取防火墙配置，配置防火墙方法之一，一共有两处，另一处:
     * @see  FirewallConfig
     *
     * @modification 修改增加网段白名单
     * @date 2016/12/8
     * @modifiedBy Hash Zhang
     */
    private void loadFirewall(Element root) throws IllegalAccessException, InvocationTargetException {
        NodeList list = root.getElementsByTagName("host");
        Map<String, List<UserConfig>> whitehost = new HashMap<>();
        Map<Pattern, List<UserConfig>> whitehostMask = new HashMap<>();

        for (int i = 0, n = list.getLength(); i < n; i++) {
            Node node = list.item(i);
            if (node instanceof Element) {
                Element e = (Element) node;
                String host = e.getAttribute("host").trim();
                String userStr = e.getAttribute("user").trim();
                if (this.firewall.existsHost(host)) {
                    throw new ConfigException("host duplicated : " + host);
                }
                String []users = userStr.split(",");
                List<UserConfig> userConfigs = new ArrayList<UserConfig>();
                for(String user : users){
                	UserConfig uc = this.users.get(user);
                    if (null == uc) {
                        throw new ConfigException("[user: " + user + "] doesn't exist in [host: " + host + "]");
                    }
                    if (uc.getSchemas() == null || uc.getSchemas().size() == 0) {
                        throw new ConfigException("[host: " + host + "] contains one root privileges user: " + user);
                    }
                    userConfigs.add(uc);
                }
                if(host.contains("*")||host.contains("%")){
                    whitehostMask.put(FirewallConfig.getMaskPattern(host),userConfigs);
                }else{
                    whitehost.put(host, userConfigs);
                }
            }
        }

        firewall.setWhitehost(whitehost);
        firewall.setWhitehostMask(whitehostMask);

        WallConfig wallConfig = new WallConfig();
        NodeList blacklist = root.getElementsByTagName("blacklist");
        for (int i = 0, n = blacklist.getLength(); i < n; i++) {
            Node node = blacklist.item(i);
            if (node instanceof Element) {
            	Element e = (Element) node;
             	String check = e.getAttribute("check");
             	if (null != check) {
             		firewall.setCheck(Boolean.parseBoolean(check));
				}

                Map<String, Object> props = ConfigUtil.loadElements((Element) node);
                ParameterMapping.mapping(wallConfig, props);
            }
        }
        firewall.setWallConfig(wallConfig);
        firewall.init();

    }

    private void loadUsers(Element root) {
        NodeList list = root.getElementsByTagName("user");
        for (int i = 0, n = list.getLength(); i < n; i++) {
            Node node = list.item(i);
            if (node instanceof Element) {
                Element e = (Element) node;
                String name = e.getAttribute("name");
                //huangyiming add  
                String defaultAccount = e.getAttribute("defaultAccount");

                UserConfig user = new UserConfig();
                Map<String, Object> props = ConfigUtil.loadElements(e);
                String password = (String)props.get("password");
                String usingDecrypt = (String)props.get("usingDecrypt");
                String passwordDecrypt = DecryptUtil.shardingcatDecrypt(usingDecrypt,name,password);
                user.setName(name);
                user.setDefaultAccount(Boolean.parseBoolean(defaultAccount));
                user.setPassword(passwordDecrypt);
                user.setEncryptPassword(password);

				String benchmark = (String) props.get("benchmark");
				if(null != benchmark) {
					user.setBenchmark( Integer.parseInt(benchmark) );
				}

				String readOnly = (String) props.get("readOnly");
				if (null != readOnly) {
					user.setReadOnly(Boolean.parseBoolean(readOnly));
				}
				
				
				String schemas = (String) props.get("schemas");
                if (schemas != null) {
                    String[] strArray = SplitUtil.split(schemas, ',', true);
                    user.setSchemas(new HashSet<String>(Arrays.asList(strArray)));
                }

                //加载用户 DML 权限
                loadPrivileges(user, e);

                if (users.containsKey(name)) {
                    throw new ConfigException("user " + name + " duplicated!");
                }
                users.put(name, user);
            }
        }
    }

    private void loadPrivileges(UserConfig userConfig, Element node) {

    	UserPrivilegesConfig privilegesConfig = new UserPrivilegesConfig();

    	NodeList privilegesNodes = node.getElementsByTagName("privileges");
    	int privilegesNodesLength = privilegesNodes.getLength();
		for (int i = 0; i < privilegesNodesLength; ++i) {
			Element privilegesNode = (Element) privilegesNodes.item(i);
			String check = privilegesNode.getAttribute("check");
         	if (null != check) {
         		privilegesConfig.setCheck(Boolean.valueOf(check));
			}


			NodeList schemaNodes = privilegesNode.getElementsByTagName("schema");
			int schemaNodeLength = schemaNodes.getLength();

			for (int j = 0; j < schemaNodeLength; j++ ) {
				Element schemaNode = (Element) schemaNodes.item(j);
				String name1 = schemaNode.getAttribute("name");
				String dml1 = schemaNode.getAttribute("dml");

				int[] dml1Array = new int[ dml1.length() ];
				for(int offset1 = 0; offset1 < dml1.length(); offset1++ ) {
					dml1Array[offset1] =  Character.getNumericValue( dml1.charAt( offset1 ) );
				}

				UserPrivilegesConfig.SchemaPrivilege schemaPrivilege = new UserPrivilegesConfig.SchemaPrivilege();
				schemaPrivilege.setName( name1 );
				schemaPrivilege.setDml( dml1Array );

				NodeList tableNodes = schemaNode.getElementsByTagName("table");
				int tableNodeLength = tableNodes.getLength();
				for (int z = 0; z < tableNodeLength; z++) {

					UserPrivilegesConfig.TablePrivilege tablePrivilege = new UserPrivilegesConfig.TablePrivilege();

					Element tableNode = (Element) tableNodes.item(z);
					String name2 = tableNode.getAttribute("name");
					String dml2 = tableNode.getAttribute("dml");

					int[] dml2Array = new int[ dml2.length() ];
					for(int offset2 = 0; offset2 < dml2.length(); offset2++ ) {
						dml2Array[offset2] =  Character.getNumericValue( dml2.charAt( offset2 ) );
					}

					tablePrivilege.setName( name2 );
					tablePrivilege.setDml( dml2Array );

					schemaPrivilege.addTablePrivilege(name2, tablePrivilege);
				}

				privilegesConfig.addSchemaPrivilege(name1, schemaPrivilege);
			}
		}

		userConfig.setPrivilegesConfig(privilegesConfig);
    }

    private void loadSystem(Element root) throws IllegalAccessException, InvocationTargetException {
        NodeList list = root.getElementsByTagName("system");
        for (int i = 0, n = list.getLength(); i < n; i++) {
            Node node = list.item(i);
            if (node instanceof Element) {
                Map<String, Object> props = ConfigUtil.loadElements((Element) node);
                ParameterMapping.mapping(system, props);
            }
        }

        if (system.getFakeMySQLVersion() != null) {
            boolean validVersion = false;
            String majorMySQLVersion = system.getFakeMySQLVersion();
            /*
             * 注意！！！ 目前MySQL官方主版本号仍然是5.x, 以后万一前面的大版本号变成2位数字，
             * 比如 10.x...,下面获取主版本的代码要做修改
             */
            majorMySQLVersion = majorMySQLVersion.substring(0, majorMySQLVersion.indexOf(".", 2));
            for (String ver : SystemConfig.MySQLVersions) {
                // 这里只是比较mysql前面的大版本号
                if (majorMySQLVersion.equals(ver)) {
                    validVersion = true;
                }
            }

            if (validVersion) {
                Versions.setServerVersion(system.getFakeMySQLVersion());
            } else {
                throw new ConfigException("The specified MySQL Version (" + system.getFakeMySQLVersion()
                        + ") is not valid.");
            }
        }
    }

}
