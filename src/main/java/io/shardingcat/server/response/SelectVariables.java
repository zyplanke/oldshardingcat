
package io.shardingcat.server.response;

import com.google.common.base.Splitter;

import io.shardingcat.backend.mysql.PacketUtil;
import io.shardingcat.config.Fields;
import io.shardingcat.net.mysql.EOFPacket;
import io.shardingcat.net.mysql.FieldPacket;
import io.shardingcat.net.mysql.ResultSetHeaderPacket;
import io.shardingcat.net.mysql.RowDataPacket;
import io.shardingcat.server.ServerConnection;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shardingcat
 */
public final class SelectVariables
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectVariables.class);


    public static void execute(ServerConnection c, String sql) {

     String subSql=   sql.substring(sql.indexOf("SELECT")+6);
    List<String>  splitVar=   Splitter.on(",").omitEmptyStrings().trimResults().splitToList(subSql) ;
        splitVar=convert(splitVar);
        int FIELD_COUNT = splitVar.size();
        ResultSetHeaderPacket header = PacketUtil.getHeader(FIELD_COUNT);
        FieldPacket[] fields = new FieldPacket[FIELD_COUNT];

        int i = 0;
        byte packetId = 0;
        header.packetId = ++packetId;
        for (int i1 = 0, splitVarSize = splitVar.size(); i1 < splitVarSize; i1++)
        {
            String s = splitVar.get(i1);
            fields[i] = PacketUtil.getField(s, Fields.FIELD_TYPE_VAR_STRING);
            fields[i++].packetId = ++packetId;
        }


        ByteBuffer buffer = c.allocate();

        // write header
        buffer = header.write(buffer, c,true);

        // write fields
        for (FieldPacket field : fields) {
            buffer = field.write(buffer, c,true);
        }


        EOFPacket eof = new EOFPacket();
        eof.packetId = ++packetId;
        // write eof
        buffer = eof.write(buffer, c,true);

        // write rows
        //byte packetId = eof.packetId;

        RowDataPacket row = new RowDataPacket(FIELD_COUNT);
        for (int i1 = 0, splitVarSize = splitVar.size(); i1 < splitVarSize; i1++)
        {
            String s = splitVar.get(i1);
            String value=  variables.get(s) ==null?"":variables.get(s) ;
            row.add(value.getBytes());

        }

        row.packetId = ++packetId;
        buffer = row.write(buffer, c,true);



        // write lastEof
        EOFPacket lastEof = new EOFPacket();
        lastEof.packetId = ++packetId;
        buffer = lastEof.write(buffer, c,true);

        // write buffer
        c.write(buffer);
    }

    private static List<String> convert(List<String> in)
    {
        List<String> out=new ArrayList<>();
        for (String s : in)
        {
          int asIndex=s.toUpperCase().indexOf(" AS ");
            if(asIndex!=-1)
            {
                out.add(s.substring(asIndex+4)) ;
            }
        }
         if(out.isEmpty())
         {
             return in;
         }  else
         {
             return out;
         }


    }




    private static final Map<String, String> variables = new HashMap<String, String>();
    static {
        variables.put("@@character_set_client", "utf8");
        variables.put("@@character_set_connection", "utf8");
        variables.put("@@character_set_results", "utf8");
        variables.put("@@character_set_server", "utf8");
        variables.put("@@init_connect", "");
        variables.put("@@interactive_timeout", "172800");
        variables.put("@@license", "GPL");
        variables.put("@@lower_case_table_names", "1");
        variables.put("@@max_allowed_packet", "16777216");
        variables.put("@@net_buffer_length", "16384");
        variables.put("@@net_write_timeout", "60");
        variables.put("@@query_cache_size", "0");
        variables.put("@@query_cache_type", "OFF");
        variables.put("@@sql_mode", "STRICT_TRANS_TABLES");
        variables.put("@@system_time_zone", "CST");
        variables.put("@@time_zone", "SYSTEM");
        variables.put("@@tx_isolation", "REPEATABLE-READ");
        variables.put("@@wait_timeout", "172800");
        variables.put("@@session.auto_increment_increment", "1");

        variables.put("character_set_client", "utf8");
        variables.put("character_set_connection", "utf8");
        variables.put("character_set_results", "utf8");
        variables.put("character_set_server", "utf8");
        variables.put("init_connect", "");
        variables.put("interactive_timeout", "172800");
        variables.put("license", "GPL");
        variables.put("lower_case_table_names", "1");
        variables.put("max_allowed_packet", "16777216");
        variables.put("net_buffer_length", "16384");
        variables.put("net_write_timeout", "60");
        variables.put("query_cache_size", "0");
        variables.put("query_cache_type", "OFF");
        variables.put("sql_mode", "STRICT_TRANS_TABLES");
        variables.put("system_time_zone", "CST");
        variables.put("time_zone", "SYSTEM");
        variables.put("tx_isolation", "REPEATABLE-READ");
        variables.put("wait_timeout", "172800");
        variables.put("auto_increment_increment", "1");
    }
    

}