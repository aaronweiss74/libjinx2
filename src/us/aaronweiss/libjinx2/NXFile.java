/**
 * libjinx2: a library for loading the NX file format
 * Copyright (C) 2012 Aaron Weiss <aaronweiss74@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package us.aaronweiss.libjinx2;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * An NX file for NX loading.
 * @author Aaron Weiss
 * @version 2.0
 * @since 1.0
 */
public class NXFile {
	private static final boolean OPEN_BY_DEFAULT = true;
	private final RandomAccessFile file;
	private ByteBuffer byteBuffer;
	private SeekableLittleEndianAccessor slea;
	private NXNodeAccessor nxna;
	private int nodeId = 0;
	private long nodeOffset = 0;
	protected INXNode baseNode = null;
	protected INXNode[] nodeTable = null;
	private String[] stringTable = null;
	private long[] canvasOffsetTable = null;
	private long[] mp3OffsetTable = null;

	/**
	 * Creates an NX file from the specified <code>path</code>.
	 * @param path the path to the file to load
	 * @throws FileNotFoundException if the file cannot be found
	 * @throws IOException if something goes wrong in opening the file
	 */
	public NXFile(String path) throws  FileNotFoundException, IOException {
		this(new RandomAccessFile(path, "r"));
	}
	
	/**
	 * Creates an NX file from the specified <code>file</code>.
	 * @param file the file to load
	 * @throws IOException if something goes wrong in opening the file
	 */
	public NXFile(RandomAccessFile file) throws IOException {
		this(file, OPEN_BY_DEFAULT);
	}
	
	/**
	 * Creates an NX file from the specified <code>file</code>.
	 * @param file the file to load
	 * @param openNow whether or not to open the file now
	 * @throws IOException if something goes wrong in opening the file
	 */
	public NXFile(RandomAccessFile file, boolean openNow) throws IOException {
		this.file = file;
		if (openNow) this.open();
	}
	
	/**
	 * Opens and initializes the NX File.
	 * @throws IOException if something goes wrong in opening the file
	 */
	public void open() throws IOException {
		FileChannel fileChannel = file.getChannel();
		byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
		slea = new SeekableLittleEndianAccessor(byteBuffer);
	}
	
	/**
	 * Parses all of the nodes from the NX file using an <code>NXNodeAccessor</code>.
	 * @throws NXException if the file fails to parse
	 */
	public void parse() throws NXException {
		parse(false);
	}
	
	/**
	 * Parses all of the nodes from the NX file.
	 * @param useAccessor whether or not to use the NXNodeAccessor
	 * @throws NXException if the file fails to parse
	 */
	public void parse(boolean useAccessor) throws NXException {
		if (slea == null) {
			throw new NXException("Failed to parse NX file as it has not yet been opened.");
		} else if (slea.getUInt() != 0x33474B50) {
			throw new NXException("Unable to parse NX file as the header was not found.");
		}
		this.parseStringTable();
		this.parseCanvasTable();
		this.parseMP3Table();
		if (!useAccessor) {
			this.parseAllNodes();
		} else {
			this.prepareNXNodeAccessor();
		}
	}
	
	/**
	 * Parses the string table from the NX file.
	 * @throws NXException if the string table fails to parse
	 */
	protected void parseStringTable() throws NXException {
		slea.seek(16);
		long stringCount = slea.getUInt();
		if (stringCount > Integer.MAX_VALUE) {
			throw new NXException("Unable to parse string table as the amount of strings is too high.");
		} else if (stringCount < 1) {
			throw new NXException("Unable to parse string table due to absense of strings.");
		}
		// n.b. the NX specification defines offsets as uint64, but Java is incapable of working with ulong.
		long stringOffset = slea.getLong();
		if (stringOffset < 0) {
			throw new NXException("Unable to parse string table as it is out of range.");
		}
		slea.seek(stringOffset);
		stringTable = new String[(int) stringCount];
		for (int i = 0; i < stringCount; ++i) {
			stringTable[i] = slea.getUTFString();
		}
	}
	
	/**
	 * Parses the canvas table from the NX file.
	 * @throws NXException if the canvas table fails to parse
	 */
	protected void parseCanvasTable() throws NXException {
		slea.seek(24);
		long canvasCount = slea.getUInt();
		if (canvasCount > Integer.MAX_VALUE) {
			throw new NXException("Unable to parse canvas table as the amount of canvases is too high.");
		} else if (canvasCount == 0) {
			canvasOffsetTable = new long[0];
			return;
		}
		// n.b. the NX specification defines offsets as uint64, but Java is incapable of working with ulong.
		long canvasOffset = slea.getLong();
		if (canvasOffset < 0) {
			throw new NXException("Unable to parse canvas table as it is out of range.");
		}
		slea.seek(canvasOffset);
		canvasOffsetTable = new long[(int) canvasCount];
		for (int i = 0; i < canvasCount; ++i) {
			canvasOffsetTable[i] = slea.getLong();
		}
	}
	
	/**
	 * Parses the mp3 table from the NX file.
	 * @throws NXException if the mp3 table fails to parse
	 */
	protected void parseMP3Table() throws NXException {
		slea.seek(40);
		long mp3Count = slea.getUInt();
		if (mp3Count > Integer.MAX_VALUE) {
			throw new NXException("Unable to parse MP3 table as the amount of MP3s is too high.");
		} else if (mp3Count == 0) {
			mp3OffsetTable = new long[0];
			return;
		}
		// n.b. the NX specification defines offsets as uint64, but Java is incapable of working with ulong.
		long mp3Offset = slea.getLong();
		if (mp3Offset < 0) {
			throw new NXException("Unable to parse MP3 table as it is out of range.");
		}
		slea.seek(mp3Offset);
		mp3OffsetTable = new long[(int) mp3Count];
		for (int i = 0; i < mp3Count; ++i) {
			mp3OffsetTable[i] = slea.getLong();
		}
	}
	
	/**
	 * Prepares the <code>NXNodeAccessor</code> instead of parsing all nodes immediately.
	 * @throws NXException if the file fails to parse
	 */
	protected void prepareNXNodeAccessor() throws NXException {
		slea.seek(4);
		long nodeCount = slea.getUInt();
		if (nodeCount > Integer.MAX_VALUE) {
			throw new NXException("Unable to parse nodes as the amount of nodes is too high.");
		} else if (nodeCount < 1) {
			throw new NXException("Unable to parse nodes due to the absence of nodes.");
		}
		long baseNodeOffset = slea.getLong();
		if (baseNodeOffset < 0) {
			throw new NXException("Unable to parse node block as it is out of range.");
		}
		this.nodeOffset = baseNodeOffset;
		slea.seek(baseNodeOffset);
		nodeTable = new INXNode[(int) nodeCount];
		baseNode = parseNode(null, false);
		slea.seek(baseNodeOffset);
		nxna = new NXNodeAccessor(slea.getBytes((int) (20 * nodeCount)), this);
	}
	
	/**
	 * Parses all of the nodes from the NX file.
	 * @throws NXException if any nodes fail to parse
	 */
	protected void parseAllNodes() throws NXException {
		slea.seek(4);
		long nodeCount = slea.getUInt();
		if (nodeCount > Integer.MAX_VALUE) {
			throw new NXException("Unable to parse nodes as the amount of nodes is too high.");
		} else if (nodeCount < 1) {
			throw new NXException("Unable to parse nodes due to the absence of nodes.");
		}
		long baseNodeOffset = slea.getLong();
		if (baseNodeOffset < 0) {
			throw new NXException("Unable to parse node block as it is out of range.");
		}
		this.nodeOffset = baseNodeOffset;
		slea.seek(baseNodeOffset);
		nodeTable = new INXNode[(int) nodeCount];
		baseNode = parseNode(null);
		stringTable = null;
		canvasOffsetTable = null;
		mp3OffsetTable = null;
	}
	
	/**
	 * Parses an individual node from the NX file.
	 * @param parent the parent of the node
	 * @return the node that was parsed
	 * @throws NXException if the node is unknown
	 */
	protected INXNode parseNode(INXNode parent) throws NXException {
		return this.parseNode(parent, true);
	}
	
	/**
	 * Parses an individual node from the NX file.
	 * @param parent the parent of the node
	 * @param parseChildren whether or not to parse the children completely
	 * @return the node that was parsed
	 * @throws NXException if the node is unknown
	 */
	protected INXNode parseNode(INXNode parent, boolean parseChildren) throws NXException {
		String name = stringTable[(int) slea.getUInt()]; // 4 bytes
		int childCount = slea.getUShort(); // 2 bytes
		int typeId = slea.getUShort(); // 2 bytes
		NXNodeType type = NXNodeType.fromTypeId(typeId);
		INXNode ret = null;
		switch (type) {
			case Null:
				ret = new NXNullNode(name, this, parent);
				slea.skip(8); // 8 bytes
				break;
			case Integer:
				ret = new NXIntegerNode(name, slea.getInt(), this, parent); // 4 bytes
				slea.skip(4); // 4 bytes
				break;
			case Double:
				ret = new NXDoubleNode(name, slea.getDouble(), this, parent); // 8 bytes
				break;
			case String:
				ret = new NXStringNode(name, stringTable[(int) slea.getUInt()], this, parent); // 4 bytes
				slea.skip(4); // 4 bytes
				break;
			case Point:
				ret = new NXPointNode(name, slea.getPoint(), this, parent); // 8 bytes
				break;
			case Canvas:
				ret = new NXCanvasNode(name, slea, getCanvasOffset(slea.getUInt()), this, parent); // 4 bytes
				slea.skip(4); // 4 bytes
				break;
			case MP3:
				ret = new NXMP3Node(name, slea, getMP3Offset(slea.getUInt()), this, parent); // 4 bytes
				slea.skip(4); // 4 bytes
				break;
			default:
			case Unknown:
				slea.skip(8); // 8 bytes
				throw new NXException("Unable to parse node as a result of an unknown node type (" + typeId + ").");
				
		}
		nodeTable[nodeId++] = ret;
		ret.setChildCount(childCount);
		if (childCount == 0 || !parseChildren) {
			slea.skip(4); // 4 bytes
			return ret;
		}
		while (childCount > 0) {
			childCount--;
			long childOffset = slea.getUInt() * 20 + nodeOffset; // 4 bytes
			slea.seek(childOffset); 
			ret.setChildOffset(childOffset);
			ret.addParsedChild(this.parseNode(ret, parseChildren));
		}
		return ret;
	}
	
	/**
	 * Parses all of the direct children of a node.
	 * @param parent the parent node to parse
	 * @return an array of the children of the node
	 * @throws NXException if the node is unknown
	 */
	protected INXNode[] parseNodeChildren(INXNode parent) throws NXException {
		int childCount = parent.getChildCount();
		INXNode[] children = null;
		if (childCount > 0) {
			children = new INXNode[childCount];
			slea.seek(parent.getChildOffset());
			while (childCount > 0) {
				children[parent.getChildCount() - childCount] = parseNode(parent, false);
				childCount--;
			}
		}
		return children;
	}
	
	/**
	 * Looks up the string at the specified offset.
	 * @param stringTableOffset the offset to look up
	 * @return the string at the offset
	 */
	protected String lookUp(long stringTableOffset) {
		return stringTable[(int) stringTableOffset];
	}
	
	/**
	 * Gets the canvas offset from the specified <code>index</code> in the canvas table.
	 * @param index the index in the table
	 * @return the canvas offset
	 */
	protected long getCanvasOffset(long index) {
		if (canvasOffsetTable.length > 0) {
			return canvasOffsetTable[(int) index];
		} else {
			return -1;
		}
	}
	
	/**
	 * Gets the mp3 offset from the specified <code>index</code> in the mp3 table. 
	 * @param index the index in the table
	 * @return the mp3 offset
	 */
	protected long getMP3Offset(long index) {
		if (mp3OffsetTable.length > 0) {
			return mp3OffsetTable[(int) index];
		} else {
			return -1;
		}
	}
	
	/**
	 * Gets the base node from this file.
	 * @return the base node of this file
	 */
	public INXNode getBaseNode() {
		return baseNode;
	}
	
	/**
	 * Resolves a node path to the node.
	 * @param path the path to the node
	 * @return the node of the path
	 */
	public INXNode resolvePath(String path) {
		String[] splitPath = path.split("/");
		INXNode workingNode = baseNode;
		try {
			for (String s : splitPath) {
				if (s == ".") {
					continue;
				} else if (s == "..") {
					workingNode = workingNode.getParent();
				} else {
					workingNode = workingNode.getChild(s);
				}
			}
			return workingNode;
		} catch (NullPointerException e) {
			return null;
		}
	}
	
	/**
	 * Gets an <code>NXNodeAccessor</code> for parsing node data.
	 * @return an <code>NXNodeAccessor</code> for parsing node data
	 */
	public NXNodeAccessor getNXNodeAccessor() {
		return nxna;
	}
	
	/**
	 * Closes the NX file and cleans up.
	 * @throws IOException if the file fails to close
	 */
	public void close() throws IOException {
		byteBuffer.clear();
		byteBuffer = null;
		slea = null;
		file.close();
		nodeId = 0;
		baseNode = null;
		nodeTable = null;
		stringTable = null;
		canvasOffsetTable = null;
		mp3OffsetTable = null;
	}
}
