
package io.shardingcat.net.mysql;

import java.nio.ByteBuffer;

import io.shardingcat.backend.mysql.BufferUtil;
import io.shardingcat.backend.mysql.MySQLMessage;
import io.shardingcat.buffer.BufferArray;
import io.shardingcat.net.FrontendConnection;

/**
 * From server to client after command, if no error and result set -- that is,
 * if the command was a query which returned a result set. The Result Set Header
 * Packet is the first of several, possibly many, packets that the server sends
 * for result sets. The order of packets for a result set is:
 * 
 * <pre>
 * (Result Set Header Packet)   the number of columns
 * (Field Packets)              column descriptors
 * (EOF Packet)                 marker: end of Field Packets
 * (Row Data Packets)           row contents
 * (EOF Packet)                 marker: end of Data Packets
 * 
 * Bytes                        Name
 * -----                        ----
 * 1-9   (Length-Coded-Binary)  field_count
 * 1-9   (Length-Coded-Binary)  extra
 * 
 * @see http://forge.mysql.com/wiki/MySQL_Internals_ClientServer_Protocol#Result_Set_Header_Packet
 * </pre>
 * 
 * @author shardingcat
 */
public class ResultSetHeaderPacket extends MySQLPacket {

    public int fieldCount;
    public long extra;

    public void read(byte[] data) {
        MySQLMessage mm = new MySQLMessage(data);
        this.packetLength = mm.readUB3();
        this.packetId = mm.read();
        this.fieldCount = (int) mm.readLength();
        if (mm.hasRemaining()) {
            this.extra = mm.readLength();
        }
    }

    @Override
    public ByteBuffer write(ByteBuffer buffer, FrontendConnection c,boolean writeSocketIfFull) {
        int size = calcPacketSize();
        buffer = c.checkWriteBuffer(buffer, c.getPacketHeaderSize() + size,writeSocketIfFull);
        BufferUtil.writeUB3(buffer, size);
        buffer.put(packetId);
        BufferUtil.writeLength(buffer, fieldCount);
        if (extra > 0) {
            BufferUtil.writeLength(buffer, extra);
        }
        return buffer;
    }
    
    public void write(BufferArray bufferArray) {
		int size = calcPacketSize();
		ByteBuffer buffer = bufferArray
				.checkWriteBuffer(packetHeaderSize + size);
		BufferUtil.writeUB3(buffer, size);
		buffer.put(packetId);
		BufferUtil.writeLength(buffer, fieldCount);
		if (extra > 0) {
			BufferUtil.writeLength(buffer, extra);
		}
	}

    @Override
    public int calcPacketSize() {
        int size = BufferUtil.getLength(fieldCount);
        if (extra > 0) {
            size += BufferUtil.getLength(extra);
        }
        return size;
    }

    @Override
    protected String getPacketInfo() {
        return "MySQL ResultSetHeader Packet";
    }

	

}