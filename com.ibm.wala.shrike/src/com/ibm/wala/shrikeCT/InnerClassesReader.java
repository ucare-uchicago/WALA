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

/**
 * This class reads InnerClasses attributes.
 */
public final class InnerClassesReader extends ClassReaderAttribute {
  /**
   * Build a reader for the attribute 'iter'.
   */
  public InnerClassesReader(ClassReader.AttrIterator iter) throws InvalidClassFileException {
    super(iter, "InnerClasses");

    checkSize(attr, 8);
    int count = cr.getUShort(attr + 6);
    checkSizeEquals(attr + 8, 8 * count);
  }

  /**
   * @return the raw values that make up this attribute
   */
  public int[] getRawTable() {
    int count = cr.getUShort(attr + 6);
    int[] r = new int[count * 4];
    for (int i = 0; i < r.length; i++) {
      r[i] = cr.getUShort(attr + 8 + i * 2);
    }
    return r;
  }
}