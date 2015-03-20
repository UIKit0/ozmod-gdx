/*
/*
OZMod - Java Sound Library
Copyright (C) 2012 by Igor Kravtchenko

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

Contact the author: igor@tsarevitch.org
 */

package ozmod;


/**
 * A Class allowing to pass data in from disk or an URL.
 */
public class SeekableBytes {
	
	public static enum Endian {
		BIGENDIAN, LITTLEENDIAN;
	}

	/**
	 * Creates a PipeIn from a disk file.
	 * 
	 * @param _file
	 *            The file from the disk.
	 * @param _endianness
	 *            BIGENDIAN or LITTLEENDIAN depending the data you want to
	 *            transfer.
	 */
	public SeekableBytes(byte[] buffer, Endian _endianness) {
		content_=buffer;
		endianness_=_endianness;
	}

	/**
	 * Reads a portion of the buffer previously loaded.
	 * 
	 * @param _buf
	 *            The buffer to store the data to.
	 * @param _off
	 *            The zero indexed offset from the start of the buffer.
	 * @param _len
	 *            Number of bytes to read.
	 */
	public void read(byte[] _buf, int _off, int _len) {
		for (int i = _off; i < _off + _len; i++)
			_buf[i] = content_[pos_++];
	}

	/**
	 * Reads one signed byte.
	 * 
	 * @return The signed byte.
	 */
	public byte readByte() {
		return content_[pos_++];
	}

	/**
	 * Reads one unsigned byte.
	 * 
	 * @return The unsigned byte.
	 */
	public int readUByte() {
		return content_[pos_++] & 0xff;
	}

	/**
	 * Reads one signed word.
	 * 
	 * @return The signed word.
	 */
	public short readShort() {
		int b1 = content_[pos_++];
		int b2 = content_[pos_++];

		if (Endian.BIGENDIAN.equals(endianness_)) {
			b2 &= 0xff;
			return (short) ((b1 << 8) | b2);
		}
		b1 &= 0xff;
		return (short) ((b2 << 8) | b1);
	}

	/**
	 * Reads one unsigned word.
	 * 
	 * @return The unsigned word.
	 */
	public short readUShort() {
		int b1 = content_[pos_++];
		int b2 = content_[pos_++];
		b1 &= 0xff;
		b2 &= 0xff;
		if (Endian.BIGENDIAN.equals(endianness_))
			return (short) ((b1 << 8) | b2);
		else
			return (short) ((b2 << 8) | b1);
	}

	/**
	 * Reads one signed integer (32 bits).
	 * 
	 * @return The signed integer.
	 */
	public int readInt() {
		int b1 = content_[pos_++];
		int b2 = content_[pos_++];
		int b3 = content_[pos_++];
		int b4 = content_[pos_++];
		b1 &= 0xff;
		b2 &= 0xff;
		b3 &= 0xff;
		b4 &= 0xff;
		if (Endian.BIGENDIAN.equals(endianness_))
			return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
		else
			return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
	}

	/**
	 * Reads the entire internal buffer.
	 * 
	 * @param _buf
	 *            The buffer for recopy.
	 */
	public void readFully(byte[] _buf) {
		int len = _buf.length;
		for (int i = 0; i < len; i++)
			_buf[i] = readByte();
	}

	/**
	 * Seeks the current position in the internal buffer.
	 * 
	 * @param _pos
	 *            The seek position in bytes.
	 */
	public void seek(int _pos) {
		pos_ = _pos;
	}

	/**
	 * Gets the current position in the internal buffer.
	 * 
	 * @return The current position in bytes.
	 */
	public int tell() {
		return pos_;
	}

	/**
	 * Forwards the current position of the internal buffer.
	 * 
	 * @param _fw
	 *            Number of bytes to forward. The value can be negative.
	 */
	public void forward(int _fw) {
		pos_ += _fw;
	}

	// File file_ = null;
	// URL url_ = null;
	byte[] content_;
	int pos_ = 0;
	protected Endian endianness_;
}
