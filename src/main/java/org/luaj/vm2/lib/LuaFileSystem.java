package org.luaj.vm2.lib;

import java.io.IOException;

/**
 * What the host gives to {@link IoLib}: a way to open files and the three
 * standard streams. This library never touches {@code java.io} files by
 * itself, so the host keeps the whole file policy (working directory, allowed
 * paths, security profile) and decides what "a file" means on its platform.
 * <p>
 * Only what {@code io} needs is here. Listing a directory, globbing or reading
 * a modification time are not part of {@code io}: the engine flavor of PlantUML
 * offers them as {@code plantuml.*} functions.
 *
 * @see IoLib
 * @see LuaFile
 */
public interface LuaFileSystem {

	/**
	 * Opens a file.
	 * @param path the path, as written by the script
	 * @param mode one of {@code "r"}, {@code "w"}, {@code "a"}, {@code "r+"},
	 *             {@code "w+"}, {@code "a+"} (the {@code b} of binary mode is
	 *             already removed; bytes are never translated)
	 * @return the open file
	 * @throws IOException if the file cannot be opened; the message is shown
	 *                     to the script after the path (for instance
	 *                     {@code "No such file or directory"})
	 */
	LuaFile open(String path, String mode) throws IOException;

	/** @return the standard input of the host, or a file that is always at its end */
	LuaFile stdin();

	/** @return the standard output of the host */
	LuaFile stdout();

	/** @return the standard error of the host */
	LuaFile stderr();
}
