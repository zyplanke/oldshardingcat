
package io.shardingcat.net.mysql;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.shardingcat.backend.mysql.BufferUtil;
import io.shardingcat.backend.mysql.MySQLMessage;
import io.shardingcat.buffer.BufferArray;
import io.shardingcat.net.FrontendConnection;

/**
 * From server to client. One packet for each row in the result set.
 * 
 * <pre>
 * Bytes                   Name
 * -----                   ----
 * n (Length Coded String) (column value)
 * ...
 * 
 * (column value):         The data in the column, as a character string.
 *                         If a column is defined as non-character, the
 *                         server converts the value into a character
 *                         before sending it. Since the value is a Length
 *                         Coded String, a NULL can be represented with a
 *                         single byte containing 251(see the description
 *                         of Length Coded Strings in section "Elements" above).
 * 
 * @see http://forge.mysql.com/wiki/MySQL_Internals_ClientServer_Protocol#Row_Data_Packet
 * </pre>
 * 
 * @author shardingcat
 */
public class RowDataPacket extends MySQLPacket {
	private static final byte NULL_MARK = (byte) 251;
    private static final byte EMPTY_MARK = (byte) 0;

	public byte[] value;
	public int fieldCount;
	public final List<byte[]> fieldValues;

	public RowDataPacket(int fieldCount) {
		this.fieldCount = fieldCount;
		this.fieldValues = new ArrayList<byte[]>(fieldCount);
	}

	public void add(byte[] value) {
		//这里应该修改value
		fieldValues.add(value);
	}
	public void addFieldCount(int add) {
		//这里应该修改field
		fieldCount=fieldCount+add;
	}
	
	public void read(byte[] data) {
		value = data;
		MySQLMessage mm = new MySQLMessage(data);
		packetLength = mm.readUB3();
		packetId = mm.read();
		for (int i = 0; i < fieldCount; i++) {
			fieldValues.add(mm.readBytesWithLength());
		}
	}

	@Override
	public ByteBuffer write(ByteBuffer bb, FrontendConnection c,
			boolean writeSocketIfFull) {
		bb = c.checkWriteBuffer(bb, c.getPacketHeaderSize(), writeSocketIfFull);
		BufferUtil.writeUB3(bb, calcPacketSize());
		bb.put(packetId);
		for (int i = 0; i < fieldCount; i++) {
			byte[] fv = fieldValues.get(i);
			if (fv == null ) {
				bb = c.checkWriteBuffer(bb, 1, writeSocketIfFull);
				bb.put(RowDataPacket.NULL_MARK);
			}else if (fv.length == 0) {
                bb = c.checkWriteBuffer(bb, 1, writeSocketIfFull);
                bb.put(RowDataPacket.EMPTY_MARK);
            }
            else {
				bb = c.checkWriteBuffer(bb, BufferUtil.getLength(fv),
						writeSocketIfFull);
				BufferUtil.writeLength(bb, fv.length);
				bb = c.writeToBuffer(fv, bb);
			}
		}
		return bb;
	}

	@Override
	public int calcPacketSize() {
		int size = 0;
		for (int i = 0; i < fieldCount; i++) {
			byte[] v = fieldValues.get(i);
			size += (v == null || v.length == 0) ? 1 : BufferUtil.getLength(v);
		}
		return size;
	}

	@Override
	protected String getPacketInfo() {
		return "MySQL RowData Packet";
	}

	public void write(BufferArray bufferArray) {
		int size = calcPacketSize();
		ByteBuffer buffer = bufferArray.checkWriteBuffer(packetHeaderSize + size);
		BufferUtil.writeUB3(buffer, size);
		buffer.put(packetId);
		for (int i = 0; i < fieldCount; i++) {
			byte[] fv = fieldValues.get(i);
			if (fv == null) {
				buffer = bufferArray.checkWriteBuffer(1);
				buffer.put(RowDataPacket.NULL_MARK);
			} else if (fv.length == 0) {
				buffer = bufferArray.checkWriteBuffer(1);
				buffer.put(RowDataPacket.EMPTY_MARK);
			} else {
				buffer = bufferArray.checkWriteBuffer(BufferUtil
						.getLength(fv.length));
				BufferUtil.writeLength(buffer, fv.length);
				bufferArray.write(fv);
			}
		}
	}

}