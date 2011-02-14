/**
 * 
 */
package com.googlecode.jue.file;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.googlecode.jue.util.ByteDynamicArray;
import com.googlecode.jue.util.ByteUtil;

/**
 * ADrop的转换读取类
 * @author noah
 *
 */
public class DropTransfer {
	
	/**
	 * 块读取Channel
	 */
	private BlockFileChannel blockChannel;

	/**
	 * 创建一个DropTransfer
	 * @param blockChannel
	 */
	public DropTransfer(BlockFileChannel blockChannel) {
		super();
		this.blockChannel = blockChannel;
	}
	
	/**
	 * 将文件头转换成ByteBuffer
	 * @param header
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer headerToByteBuffer(FileHeader header) {
		ByteBuffer buffer = ByteBuffer.allocate(FileHeader.HEADER_LENGHT);		
		
		buffer.putLong(header.getFileTail());
		buffer.putInt(header.getKeyTreeMin());
		buffer.putInt(header.getValueRevTreeMin());
		buffer.put(header.getValueCompressed());
		buffer.put(header.getCompressionCodec());
		buffer.putInt(header.getBlockSize());
		buffer.flip();
		
		return buffer;
	}
	
	/**
	 * 文件尾转换成ByteBuffer
	 * @param tail
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer tailToByteBuffer(FileTail tail) {
		ByteBuffer buffer = ByteBuffer.allocate(FileTail.TAIL_LENGHT);
	    
	    buffer.putInt(tail.getRevision());
	    buffer.putLong(tail.getRootNode());
	    buffer.putInt(tail.getAvgKeyLen());
	    buffer.putInt(tail.getAvgValueLen());
	    buffer.putLong(tail.getEntryCount());
	    buffer.flip();
	
		return buffer;
	}

	/**
	 * 将keyNode转换成Byteuffer
	 * @param keyNode
	 * @return
	 */
	public ByteBuffer keyNodeToByteBuffer(KeyNode keyNode) {
		ByteDynamicArray array = new ByteDynamicArray();
		// 是否叶节点
		array.add(keyNode.getLeaf());
		byte[][] keys = keyNode.getKeys();
		// 关键字的数量
		array.add(ByteUtil.int2byte(keys.length));
		for (int i = 0; i < keys.length; ++i) {
			byte[] key = keys[i];
			// 键的长度
			array.add(ByteUtil.int2byte(key.length));
			// 键的内容
			array.add(key);
		}
		// 添加子树或者键记录的地址
		long[] childPos = keyNode.getChildOrKeyPos();
		for (int i = 0; i < childPos.length; ++i) {
			array.add(ByteUtil.long2byte(childPos[i]));
		}
		byte[] b = array.toByteArray();
		ByteBuffer buffer = ByteBuffer.allocate(b.length);
		buffer.put(b);
		return buffer;
	}
	
	public ByteBuffer keyRecordToByteBuffer(KeyRecord keyRecord) {
		ByteDynamicArray array = new ByteDynamicArray();
		// 标志符
		array.add(keyRecord.getFlag());
		byte[] key = keyRecord.getKey();
		// 关键字的长度
		array.add(ByteUtil.int2byte(key.length));
		// 关键字
		array.add(key);
		// Value的版本树的根节点
		array.add(ByteUtil.long2byte(keyRecord.getRevRootNode()));
		// 当前Key的版本
		array.add(ByteUtil.int2byte(keyRecord.getRevision()));
		// 最新版本的Value记录地址
		array.add(ByteUtil.long2byte(keyRecord.getLastestValue()));
		byte[] b = array.toByteArray();
		ByteBuffer buffer = ByteBuffer.allocate(b.length);
		buffer.put(b);
		return buffer;
	}
	
	/**
	 * ValueRevNode转换成ByteBuffer
	 * @param valueRevNode
	 * @return
	 */
	public ByteBuffer valueRevNodeToByteBuffer(ValueRevNode valueRevNode) {
		ByteDynamicArray array = new ByteDynamicArray();
		// 是否叶节点
		array.add(valueRevNode.getLeaf());
		int[] revisions = valueRevNode.getRevisions();
		// 关键字的数量
		array.add(ByteUtil.int2byte(revisions.length));
		for (int i = 0; i < revisions.length; ++i) {
			array.add(ByteUtil.int2byte(revisions[i]));
		}
		// 添加子树地址
		long[] childPos = valueRevNode.getChildOrKeyPos();
		for (int i = 0; i < childPos.length; ++i) {
			array.add(ByteUtil.long2byte(childPos[i]));
		}
		byte[] b = array.toByteArray();
		ByteBuffer buffer = ByteBuffer.allocate(b.length);
		buffer.put(b);
		return buffer;
	}
	
	/**
	 * ValueRecord转换成ByteBuffer
	 * @param valueRecord
	 * @return
	 */
	public ByteBuffer valueRecordToByteBuffer(ValueRecord valueRecord) {
		ByteDynamicArray array = new ByteDynamicArray();
		// 标志符
		array.add(valueRecord.getFlag());
		byte[] value = valueRecord.getValue();
		// Value的长度
		array.add(ByteUtil.int2byte(value.length));
		// 加入Value
		array.add(value);
		// Value的版本
		array.add(ByteUtil.int2byte(valueRecord.getRevision()));		
		byte[] b = array.toByteArray();
		ByteBuffer buffer = ByteBuffer.allocate(b.length);
		buffer.put(b);
		return buffer;
	}
	
	/**
	 * 读取文件头
	 * @return
	 * @throws ChecksumException 
	 * @throws IOException 
	 */
	public FileHeader readHeader() throws IOException, ChecksumException {
		ByteBuffer buffer = ByteBuffer.allocate(FileHeader.HEADER_LENGHT);
		blockChannel.read(buffer, 0, true);
		buffer.flip();
		long fileTail = buffer.getLong();
		int keyTreeMin = buffer.getInt();
		int valueRevTreeMin = buffer.getInt();
		byte valueCompressed = buffer.get();
		byte compressionCodec = buffer.get();
		int blockSize = buffer.getInt();
		
		FileHeader header = new FileHeader();
		header.setFileTail(fileTail);
		header.setKeyTreeMin(keyTreeMin);
		header.setValueRevTreeMin(valueRevTreeMin);
		header.setValueCompressed(valueCompressed);
		header.setCompressionCodec(compressionCodec);
		header.setBlockSize(blockSize);
		return header;
	}

	/**
	 * 读取文件尾
	 * @param position
	 * @return
	 * @throws IOException
	 * @throws ChecksumException
	 */
	public FileTail readTail(long position) throws IOException, ChecksumException {
		ByteBuffer buffer = ByteBuffer.allocate(FileTail.TAIL_LENGHT);
		blockChannel.read(buffer, position, true);
		buffer.flip();
		int revision = buffer.getInt();
		long rootNode = buffer.getLong();
	    int avgKeyLen = buffer.getInt();
	    int avgValueLen = buffer.getInt();
	    long entryCount = buffer.getLong();
	    
	    FileTail tail = new FileTail();
	    tail.setRevision(revision);
	    tail.setRootNode(rootNode);
	    tail.setAvgKeyLen(avgKeyLen);
	    tail.setAvgValueLen(avgValueLen);
	    tail.setEntryCount(entryCount);
		return tail;
	}
	
	/**
	 * 从磁盘上，读取KeyNode
	 * @param position 读取的位置
	 * @return
	 * @throws IOException
	 * @throws ChecksumException
	 */
	public KeyNode readKeyNode(long position) throws IOException, ChecksumException {
		// 读取偏移量
		long offset = position;
		ByteBuffer buffer = ByteBuffer.allocate(5);
		blockChannel.read(buffer, offset, true);
		buffer.flip();
		offset += buffer.limit();
		// 是否叶子节点
		byte leaf = buffer.get();
		// 键的数量
		int keyCount = buffer.getInt();
		byte[][] keys = new byte[keyCount][];
		ByteBuffer keyLengthBuffer = ByteBuffer.allocate(4);
		for (int i = 0; i < keyCount; ++i) {
			// 读取键的长度
			blockChannel.read(keyLengthBuffer, offset, true);
			keyLengthBuffer.flip();
			offset += keyLengthBuffer.limit();
			int keyLength = keyLengthBuffer.getInt();
			// 读取键的内容
			ByteBuffer keyBuffer = ByteBuffer.allocate(keyLength);
			blockChannel.read(keyBuffer, offset, true);
			keyBuffer.flip();
			offset += keyBuffer.limit();
			keys[i] = keyBuffer.array();
			
			keyLengthBuffer.clear();
		}
		// 判断是否是叶子节点以及子树或者键记录的数量
		int childLenght = (KeyNode.TRUE_BYTE == leaf) ? keyCount : keyCount + 1;
		long[] childOrKeyPos = new long[childLenght];
		// 子树或者键记录的地址
		ByteBuffer childPosBuffer = ByteBuffer.allocate(childLenght * 8);
		blockChannel.read(childPosBuffer, offset, true);
		childPosBuffer.flip();
		for (int j = 0; j < childLenght; ++j) {
			childOrKeyPos[j] = childPosBuffer.getLong();
		}
		KeyNode keyNode = new KeyNode(leaf, keys, childOrKeyPos);
		return keyNode;
	}
	
	/**
	 * 读取KeyRecord
	 * @param position 读取的位置
	 * @return
	 * @throws IOException
	 * @throws ChecksumException
	 */
	public KeyRecord readKeyRecord(long position) throws IOException, ChecksumException {
		// 读取偏移量
		long offset = position;
		ByteBuffer buffer = ByteBuffer.allocate(5);
		blockChannel.read(buffer, offset, true);
		buffer.flip();
		offset += buffer.limit();
		// 标识符
		byte flag = buffer.get();
		// 键的长度
		int keyCount = buffer.getInt();
		// 键的长度 + Value的版本树的根节点 + 当前Key的版本 + 最新版本的Value记录地址
		int readLength = keyCount + 8 + 4 + 8;
		ByteBuffer buf = ByteBuffer.allocate(readLength);
		blockChannel.read(buf, offset, true);
		buf.flip();

		byte[] key = new byte[keyCount];
		buf.get(key);
		long revRootNode = buf.getLong();
		int revision = buf.getInt();
		long lastestValue = buf.getLong();
		
		KeyRecord keyRecord = new KeyRecord(flag, key, revRootNode, revision, lastestValue);
		return keyRecord;
	}
	
	/**
	 * 读取ValueRevNode
	 * @param position 读取的位置
	 * @return
	 * @throws IOException
	 * @throws ChecksumException
	 */
	public ValueRevNode readValueRevNode(long position) throws IOException, ChecksumException {
		// 读取偏移量
		long offset = position;
		ByteBuffer buffer = ByteBuffer.allocate(5);
		blockChannel.read(buffer, offset, true);
		buffer.flip();
		offset += buffer.limit();
		// 是否叶子节点
		byte leaf = buffer.get();
		// 键的数量
		int keyCount = buffer.getInt();
		int[] revisions = new int[keyCount];
		ByteBuffer revisionsBuffer = ByteBuffer.allocate(keyCount * 4);
		blockChannel.read(revisionsBuffer, offset, true);
		revisionsBuffer.flip();
		offset += revisionsBuffer.limit();
		for (int i = 0; i < keyCount; ++i) {
			revisions[i] = revisionsBuffer.getInt();
		}
		// 判断是否是叶子节点以及子树或者键记录的数量
		int childLenght = (ValueRevNode.TRUE_BYTE == leaf) ? keyCount : keyCount + 1;
		long[] childOrKeyPos = new long[childLenght];
		// 子树或者Value记录的地址
		ByteBuffer childPosBuffer = ByteBuffer.allocate(childLenght * 8);
		blockChannel.read(childPosBuffer, offset, true);
		for (int j = 0; j < childLenght; ++j) {
			childOrKeyPos[j] = childPosBuffer.getLong();
		}
		ValueRevNode valueRevNode = new ValueRevNode(leaf, revisions, childOrKeyPos);
		return valueRevNode;
	}
	
	/**
	 * 读取ValueRecord
	 * @param position 读取的位置
	 * @return
	 * @throws IOException
	 * @throws ChecksumException
	 */
	public ValueRecord readValueRecord(long position) throws IOException, ChecksumException {
		// 读取偏移量
		long offset = position;
		ByteBuffer buffer = ByteBuffer.allocate(5);
		blockChannel.read(buffer, offset, true);
		buffer.flip();
		offset += buffer.limit();
		// 标识符
		byte flag = buffer.get();
		// Value长度
		int valueLength = buffer.getInt();
		// Value长度 + 当前Value的版本
		int readLength = valueLength + 4;
		ByteBuffer buf = ByteBuffer.allocate(readLength);
		blockChannel.read(buf, offset, true);
		buf.flip();
		byte[] value = new byte[valueLength];
		buf.get(value);
		int revision = buf.getInt();
		ValueRecord keyRecord = new ValueRecord(flag, value, revision);
		return keyRecord;
	}
}
