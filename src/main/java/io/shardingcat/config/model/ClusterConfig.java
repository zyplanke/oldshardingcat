
package io.shardingcat.config.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.shardingcat.config.util.ConfigException;
import io.shardingcat.config.util.ConfigUtil;
import io.shardingcat.util.SplitUtil;

/**
 * @author shardingcat
 */
public class ClusterConfig {
    private final Map<String, ShardingCatNodeConfig> nodes;
    private final Map<String, List<String>> groups;

    public ClusterConfig(Element root, int port) {
        nodes = Collections.unmodifiableMap(loadNode(root, port));
        groups = Collections.unmodifiableMap(loadGroup(root, nodes));
    }

    public Map<String, ShardingCatNodeConfig> getNodes() {
        return nodes;
    }

    public Map<String, List<String>> getGroups() {
        return groups;
    }

    private static Map<String, ShardingCatNodeConfig> loadNode(Element root, int port) {
        Map<String, ShardingCatNodeConfig> nodes = new HashMap<String, ShardingCatNodeConfig>();
        NodeList list = root.getElementsByTagName("node");
        Set<String> hostSet = new HashSet<String>();
        for (int i = 0, n = list.getLength(); i < n; i++) {
            Node node = list.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttribute("name").trim();
                if (nodes.containsKey(name)) {
                    throw new ConfigException("node name duplicated :" + name);
                }

                Map<String, Object> props = ConfigUtil.loadElements(element);
                String host = (String) props.get("host");
                if (null == host || "".equals(host)) {
                    throw new ConfigException("host empty in node: " + name);
                }
                if (hostSet.contains(host)) {
                    throw new ConfigException("node host duplicated :" + host);
                }

                String wei = (String) props.get("weight");
                if (null == wei || "".equals(wei)) {
                    throw new ConfigException("weight should not be null in host:" + host);
                }
                int weight = Integer.parseInt(wei);
                if (weight <= 0) {
                    throw new ConfigException("weight should be > 0 in host:" + host + " weight:" + weight);
                }

                ShardingCatNodeConfig conf = new ShardingCatNodeConfig(name, host, port, weight);
                nodes.put(name, conf);
                hostSet.add(host);
            }
        }
        return nodes;
    }

    private static Map<String, List<String>> loadGroup(Element root, Map<String, ShardingCatNodeConfig> nodes) {
        Map<String, List<String>> groups = new HashMap<String, List<String>>();
        NodeList list = root.getElementsByTagName("group");
        for (int i = 0, n = list.getLength(); i < n; i++) {
            Node node = list.item(i);
            if (node instanceof Element) {
                Element e = (Element) node;
                String groupName = e.getAttribute("name").trim();
                if (groups.containsKey(groupName)) {
                    throw new ConfigException("group duplicated : " + groupName);
                }

                Map<String, Object> props = ConfigUtil.loadElements(e);
                String value = (String) props.get("nodeList");
                if (null == value || "".equals(value)) {
                    throw new ConfigException("group should contain 'nodeList'");
                }

                String[] sList = SplitUtil.split(value, ',', true);

                if (null == sList || sList.length == 0) {
                    throw new ConfigException("group should contain 'nodeList'");
                }

                for (String s : sList) {
                    if (!nodes.containsKey(s)) {
                        throw new ConfigException("[ node :" + s + "] in [ group:" + groupName + "] doesn't exist!");
                    }
                }
                List<String> nodeList = Arrays.asList(sList);
                groups.put(groupName, nodeList);
            }
        }
        if (!groups.containsKey("default")) {
            List<String> nodeList = new ArrayList<String>(nodes.keySet());
            groups.put("default", nodeList);
        }
        return groups;
    }
}