/**
 * Java RTP Library (jlibrtp)
 * Copyright (C) 2009 Arne Kepp
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jlibrtp.protocols.rtp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CircularByteBuffer {	
	private int readOfs = 0; 
	
	private int writeOfs = 0;

        private int initialSize;
	
	private CircularByteBufferInputStream is;
	
	private CircularByteBufferOutputStream os;
	
	private byte[] buf;
	
	public CircularByteBuffer(int initialSize) {
		buf = new byte[initialSize];
		this.initialSize = initialSize;
		is = new CircularByteBufferInputStream(this);
		os = new CircularByteBufferOutputStream(this);
	}
	
	private synchronized int bytesLeft() {
		return buf.length - bytesUsed();
	}
	
	private synchronized int bytesUsed() {
		if(readOfs == writeOfs)
			return 0;
		
		if(readOfs < writeOfs)
			return writeOfs - readOfs;
	
		return buf.length - (writeOfs - readOfs);
	}
	
	private void doubleBuf() {
		byte[] oldBuf = buf;
		buf = new byte[oldBuf.length*2];
		
		System.arraycopy(oldBuf, 0, buf, 0, oldBuf.length);
	}
	
	private synchronized void write(byte[] data, int offset, int length) {
		while(length > bytesLeft()) {
			doubleBuf();
		}
		
		if(writeOfs + length > buf.length) {
			int endLength = buf.length - this.writeOfs;
			
			System.arraycopy(data, 0, buf, writeOfs, endLength);
			
			writeOfs = 0;
			length = length - endLength;
			offset = endLength + offset;
		}
		
		System.arraycopy(data, offset, buf, writeOfs, length);
		
		writeOfs += length;
	}
	
	private synchronized int read(byte[] buffer, int offset, int length) {
		int maxLeft= this.bytesUsed();
		int bytesLeft = length;
		
		if(maxLeft < bytesLeft) {
			bytesLeft = maxLeft;
			length = maxLeft;
		}
		
		if(readOfs + length > buf.length) {
			int endLength = buf.length - readOfs;

			System.arraycopy(buf, readOfs, buffer, offset, endLength);
			
			readOfs = 0;
			bytesLeft = bytesLeft - endLength;
			offset = offset + endLength;
		}
		
		System.arraycopy(buf, readOfs, buffer, offset, bytesLeft);
		
		readOfs += bytesLeft;
		
		return length;
	}

        public synchronized void clear() {
	    readOfs = 0;
            writeOfs = 0;
            buf = new byte[this.initialSize];
	}

	public String debugPrintFunction() {
		return this.buf.length + " " + readOfs + " " + writeOfs + " " + this.bytesLeft() + " " + this.bytesUsed(); 
	}
	
	public String debugPrintData() {
		String str = "";
		for(int i=0; i<buf.length; i++) {
			String tmpstr = "" + buf[i];
			if(i == this.readOfs) {
				tmpstr = "(" +tmpstr+ ")";
			} 
			if(i == this.writeOfs) {
				tmpstr = "[" +tmpstr+ "]";
			}
			str += " " + tmpstr;
		}
		
		return str + "   []:writeOfs ():readOfs";
	}
	
	
	public OutputStream getOutputStream() {
		return os;
	}
	
	public InputStream getInputStream() {
		return is;
	}
	
	protected class CircularByteBufferOutputStream extends OutputStream {
		CircularByteBuffer cbb;
		boolean closed = false;
		
		protected CircularByteBufferOutputStream(CircularByteBuffer cbb) {
			this.cbb = cbb;
		}
		
		public void write(int b) throws IOException {
			byte[] bytes = new byte[1];
			bytes[0] = (byte) b;
			write(bytes);
		}
		
		public void write(byte[] data) throws IOException {
			if(closed)
				throw new IOException("Stream has been closed");
			
			cbb.write(data, 0, data.length);
		}
		
		public void write(byte[] data, int offset, int length) throws IOException {
			if(closed)
				throw new IOException("Stream has been closed");
			
			cbb.write(data, offset, length);
		}
		
		public void flush() throws IOException {
			if(closed)
				throw new IOException("Stream has been closed");
			
		}
		
		public void close() throws IOException {
			closed = true;
		}
	}
	
	protected class CircularByteBufferInputStream extends InputStream {
		CircularByteBuffer cbb;
		boolean closed = false;
		
		protected CircularByteBufferInputStream(CircularByteBuffer cbb) {
			this.cbb = cbb;
		}
		
		public int available() throws IOException {
			if(closed)
				throw new IOException("Stream has been closed");
			
			return cbb.bytesUsed();
		}
		
		public void close() throws IOException {
			closed = true;
		}
		
		public boolean markSupported() {
			return false;
		}
	
		
		public int read() throws IOException {
			byte[] bytes = new byte[0];
			this.read(bytes);
			return (int) bytes[0];
		}
		
		public int read(byte[] buffer) throws IOException {
			if(closed)
				throw new IOException("Stream has been closed");
			
			return cbb.read(buffer, 0, buffer.length);
		}
		
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if(closed)
				throw new IOException("Stream has been closed");
			
			return cbb.read(buffer, offset, length);
		}
		
		public long skip(long n) throws IOException  {
			int skipn = (int) n;
			byte[] bytes = new byte[skipn];
			
			return (long) read(bytes);
		}
	}	
}
