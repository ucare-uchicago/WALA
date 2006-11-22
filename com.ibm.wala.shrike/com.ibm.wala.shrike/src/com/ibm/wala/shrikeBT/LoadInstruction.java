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
package com.ibm.wala.shrikeBT;

/**
 * This class represents local variable load instructions.
 */
public final class LoadInstruction extends Instruction {
  private int index;

  protected LoadInstruction(short opcode, int index) {
    this.index = index;
    this.opcode = opcode;
  }

  private final static LoadInstruction[] preallocated = preallocate();

  private static LoadInstruction[] preallocate() {
    LoadInstruction[] r = new LoadInstruction[5 * 16];
    for (int p = 0; p < 5; p++) {
      for (int i = 0; i < 4; i++) {
        r[p * 16 + i] = new LoadInstruction((short) (OP_iload_0 + i + p * 4), i);
      }
      for (int i = 4; i < 16; i++) {
        r[p * 16 + i] = new LoadInstruction((short) (OP_iload + p), i);
      }
    }
    return r;
  }

  public static LoadInstruction make(String type, int index) {
    int t = Util.getTypeIndex(type);
    if (t < 0 || t > TYPE_Object_index) {
      throw new IllegalArgumentException("Cannot load local of type " + type);
    }
    if (index < 16) {
      return preallocated[t * 16 + index];
    } else {
      return new LoadInstruction((short) (OP_iload + t), index);
    }
  }

  /**
   * @return the index of the local variable loaded
   */
  public int getVarIndex() {
    return index;
  }

  public String getType() {
    if (opcode < OP_iload_0) {
      return indexedTypes[opcode - OP_iload];
    } else {
      return indexedTypes[(opcode - OP_iload_0) / 4];
    }
  }

  public String getPushedType(String[] types) {
    return getType();
  }

  public byte getPushedWordSize() {
    return Util.getWordSize(getType());
  }

  public void visit(Visitor v) {
    v.visitLocalLoad(this);
  }

  public boolean equals(Object o) {
    if (o instanceof LoadInstruction) {
      LoadInstruction i = (LoadInstruction) o;
      return i.index == index && i.opcode == opcode;
    } else {
      return false;
    }
  }

  public int hashCode() {
    return opcode + index * 19801901;
  }

  public String toString() {
    return "LocalLoad(" + getType() + "," + index + ")";
  }
    /* (non-Javadoc)
   * @see com.ibm.domo.cfg.IInstruction#isPEI()
   */
  public boolean isPEI() {
    return false;
  }
}