/*******************************************************************************
 * Copyright (c) 2009-2011 Luaj.org. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 ******************************************************************************/
package org.luaj.vm2.lib;

import java.io.InputStream;

import org.luaj.vm2.Globals;

/** 
 * Interface for opening application resource files such as scripts sources.  
 * <p>
 * This is used by required to load files that are part of 
 * the application. {@link BaseLib} installs a finder that finds nothing, so the
 * host application sets {@link Globals#finder} to give scripts access to the
 * files it chooses to expose.
 * <p>
 * @see BaseLib
 * @see Globals#finder
 * @see org.luaj.vm2.lib.jse.JsePlatform 
 */
public interface ResourceFinder {
	
	/** 
	 * Try to open a file, or return null if not found.
	 * 
	 * @see BaseLib
	 * 
	 * @param filename
	 * @return InputStream, or null if not found. 
	 */
	InputStream findResource( String filename );
}