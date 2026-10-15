package org.luaj.vm2.lib;

import java.io.IOException;

/**
 * An open file, as the host sees it: a stream of bytes. {@link IoLib} turns it
 * into the Lua file objects (line reading, number parsing, formatting); the
 * host only moves bytes, and decides what the bytes are (a disk file, a
 * pipe, memory, the console).
 * <p>
 * Reads and writes are not buffered by {@link IoLib} on the write side, so a
 * host that cares about speed buffers inside its implementation.
 *
 * @see LuaFileSystem
 */
public interface LuaFile {

	/**
	 * Reads up to {@code len} bytes.
	 * @param buf the destination
	 * @param off where to start in {@code buf}
	 * @param len the maximum number of bytes
	 * @return the number of bytes read (at least 1), or -1 at the end of the file
	 * @throws IOException if the read fails, or if the file is not readable
	 */
	int read(byte[] buf, int off, int len) throws IOException;

	/**
	 * Writes {@code len} bytes.
	 * @param buf the source
	 * @param off where to start in {@code buf}
	 * @param len the number of bytes
	 * @throws IOException if the write fails, or if the file is not writable
	 */
	void write(byte[] buf, int off, int len) throws IOException;

	/**
	 * Sends what is pending to the destination.
	 * @throws IOException if the flush fails
	 */
	void flush() throws IOException;

	/**
	 * Closes the file; called at most once.
	 * @throws IOException if the close fails
	 */
	void close() throws IOException;
}
