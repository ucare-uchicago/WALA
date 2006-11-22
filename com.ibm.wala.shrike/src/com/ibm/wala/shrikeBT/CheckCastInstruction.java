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
 * This class represents checkcast instructions.
 */
final public class CheckCastInstruction extends Instruction {
  private String type;

  protected CheckCastInstruction(String type) {
    this.type = type;
    this.opcode = OP_checkcast;
  }

  public static CheckCastInstruction make(String type) {
    return new CheckCastInstruction(type.intern());
  }

  public boolean equals(Object o) {
    if (o instanceof CheckCastInstruction) {
      CheckCastInstruction i = (CheckCastInstruction) o;
      return i.type.equals(type);
    } else {
      return false;
    }
  }

  public int hashCode() {
    return 131111 + type.hashCode();
  }

  public int getPoppedCount() {
    return 1;
  }

  /**
   * @return the type to which the operand is cast
   */
  public String getType() {
    return type;
  }

  public String getPushedType(String[] types) {
    return type;
  }

  public byte getPushedWordSize() {
    return 1;
  }

  public void visit(Visitor v) {
    v.visitCheckCast(this);
  }

  public String toString() {
    return "CheckCast(" + type + ")";
  }
    /* (non-Javadoc)
   * @see com.ibm.domo.cfg.IInstruction#isPEI()
   */
  public boolean isPEI() {
    return true;
  }
}