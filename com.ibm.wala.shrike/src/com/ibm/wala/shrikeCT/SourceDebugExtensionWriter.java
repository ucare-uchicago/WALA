/*******************************************************************************
 * Copyright (c) 2002,2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.shrikeCT;

import java.io.UnsupportedEncodingException;

import com.ibm.wala.shrikeCT.ClassWriter;

public class SourceDebugExtensionWriter extends ClassWriter.Element {
	private int attrID;
	private byte[] table;
	
	public SourceDebugExtensionWriter(ClassWriter w){
		attrID = w.addCPUtf8("SourceDebugExtension");
	}
	
	public int getSize() {
	    return table == null ? 6 : 6 + table.length;
	}

	public int copyInto(byte[] buf, int offset) {
	    ClassWriter.setUShort(buf, offset, attrID);
	    ClassWriter.setInt(buf, offset + 2, getSize() - 6);
	    offset += 6;
	    if (table != null) {
	      for (int i = 0; i < table.length; i++) {
	        ClassWriter.setUByte(buf, offset, table[i]);
	        offset++;
	      }
	    }
	    return offset;
	}
	
	public void setRawTable(byte[] sourceDebug){
		for(int i = 0; i < sourceDebug.length ; i++){
			if (sourceDebug[i] < 1 || sourceDebug[i] > 0xFFFF) {
		        throw new IllegalArgumentException("Invalid CP index: " + sourceDebug[i]);
			}
		}
		 this.table = sourceDebug;
	}

	public void setDebugInfo(String sourceDebug){
		try {
			byte[] bytes = sourceDebug.getBytes("UTF8");
			setRawTable(bytes);
		} catch (UnsupportedEncodingException e){
			System.err.println(e);
		}
	}
}
