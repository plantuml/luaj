package org.luaj.vm2.lib;

/**
 * What the host gives to {@link OsLib}: the few operating system services that
 * an engine script may use. Nothing here is available to text Lua.
 */
public interface OsHost {

	/**
	 * @param name the name of an environment variable
	 * @return its value, or null if it is not defined (or not visible to scripts)
	 */
	String getenv(String name);

	/** @return processor time used by the program, in seconds */
	double clock();

	/** @return the current time, in seconds since 1970-01-01 00:00:00 UTC */
	long time();

	/**
	 * Ends the program. It should not return; if it does, {@code os.exit}
	 * raises an error in the script. A host that cannot end the process (a
	 * test, for instance) may throw: the VM turns the exception into a Lua
	 * error, which the script can catch with {@code pcall}.
	 * @param status the exit status, 0 for success
	 */
	void exit(int status);
}
