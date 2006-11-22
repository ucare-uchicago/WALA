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
package com.ibm.wala.shrikeBT.shrikeCT;

import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.wala.shrikeBT.Compiler;
import com.ibm.wala.shrikeBT.ConstantPoolReader;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.ExceptionHandler;
import com.ibm.wala.shrikeBT.Instruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.Util;
import com.ibm.wala.shrikeBT.Decoder.InvalidBytecodeException;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.ClassWriter;
import com.ibm.wala.shrikeCT.CodeReader;
import com.ibm.wala.shrikeCT.CodeWriter;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.shrikeCT.LineNumberTableReader;
import com.ibm.wala.shrikeCT.LineNumberTableWriter;
import com.ibm.wala.shrikeCT.LocalVariableTableReader;
import com.ibm.wala.shrikeCT.LocalVariableTableWriter;
import com.ibm.wala.shrikeCT.ClassWriter.Element;

/**
 * This class provides a convenient way to instrument every method in a class.
 * It assumes you are using ShrikeCT to read and write classes. It's stateful;
 * initially every method is set to the original code read from the class, but
 * you can then go in and modify the methods.
 */
final public class ClassInstrumenter {
  private boolean[] deletedMethods;
  private MethodData[] methods;
  private CodeReader[] oldCode;
  private ClassReader cr;
  private ConstantPoolReader cpr;
  private boolean createFakeLineNumbers = false;
  private int fakeLineOffset;

  /**
   * Create a class instrumenter from raw bytes.
   */
  public ClassInstrumenter(byte[] bytes) throws InvalidClassFileException {
    this(new ClassReader(bytes));
  }

  /**
   * Calling this means that methods without line numbers get fake line numbers
   * added: each bytecode instruction is treated as at line 'offset' + the
   * offset of the instruction.
   */
  public void enableFakeLineNumbers(int offset) {
    createFakeLineNumbers = true;
    fakeLineOffset = offset;
  }

  /**
   * Create a class instrumenter from a preinitialized class reader.
   */
  public ClassInstrumenter(ClassReader cr) throws InvalidClassFileException {
    this.cr = cr;
    methods = new MethodData[cr.getMethodCount()];
    oldCode = new CodeReader[methods.length];
    cpr = CTDecoder.makeConstantPoolReader(cr);
    deletedMethods = new boolean[methods.length];
  }

  /**
   * @return the reader for the class
   */
  public ClassReader getReader() {
    return cr;
  }

  /**
   * Implement this interface to instrument every method of a class using
   * visitMethods() below.
   */
  public static interface MethodExaminer {
    /**
     * Do something to the method.
     */
    public void examineCode(MethodData data);
  }

  private void prepareMethod(int i) throws InvalidClassFileException {
    if (deletedMethods[i]) {
      methods[i] = null;
    } else if (methods[i] == null) {
      ClassReader.AttrIterator iter = new ClassReader.AttrIterator();
      cr.initMethodAttributeIterator(i, iter);
      for (; iter.isValid(); iter.advance()) {
        if (iter.getName().equals("Code")) {
          CodeReader code = new CodeReader(iter);
          CTDecoder d = new CTDecoder(code, cpr);
          try {
            d.decode();
          } catch (InvalidBytecodeException e) {
            throw new InvalidClassFileException(code.getRawOffset(), e.getMessage());
          }
          MethodData md = new MethodData(d, cr.getMethodAccessFlags(i), CTDecoder.convertClassToType(cr.getName()), cr
              .getMethodName(i), cr.getMethodType(i));
          methods[i] = md;
          oldCode[i] = code;
          return;
        }
      }
    }
  }

  /**
   * Indicate that the method should be deleted from the class.
   * 
   * @param i
   *          the index of the method to delete
   */
  public void deleteMethod(int i) {
    deletedMethods[i] = true;
  }

  private final static ExceptionHandler[] noHandlers = new ExceptionHandler[0];

  //Xiangyu
  //create a empty method body and then user can apply patches later on
  public MethodData createEmptyMethodData(String name, String sig, int access) {
    //Instruction[] instructions=new Instruction[0];
    Instruction[] instructions = new Instruction[1];
    instructions[0] = ReturnInstruction.make(Constants.TYPE_void);
    ExceptionHandler[][] handlers = new ExceptionHandler[instructions.length][];
    Arrays.fill(handlers, noHandlers);
    int[] i2b = new int[instructions.length];
    for (int i = 0; i < i2b.length; i++) {
      i2b[i] = i;
    }
    MethodData md = null;
    try {
      md = new MethodData(access, Util.makeType(cr.getName()), name, sig, instructions, handlers, i2b);

    } catch (InvalidClassFileException ex) {
      ex.printStackTrace();
    }
    return md;

  }

  /**
   * Xiangyu
   * 
   *  
   */
  public void newMethod(String name, String sig, ArrayList<Instruction> instructions, int access, ClassWriter classWriter,
      ClassWriter.Element rawLines) {
    Instruction[] ins = (Instruction[]) instructions.toArray(new Instruction[instructions.size()]);
    ExceptionHandler[][] handlers = new ExceptionHandler[ins.length][];
    Arrays.fill(handlers, noHandlers);
    int[] i2b = new int[ins.length];
    for (int i = 0; i < i2b.length; i++) {
      i2b[i] = i;
    }
    MethodData md = null;
    try {
      md = new MethodData(access, Util.makeType(cr.getName()), name, sig, ins, handlers, i2b);
    } catch (InvalidClassFileException ex) {
      ex.printStackTrace();
    }
    CTCompiler compiler = new CTCompiler(classWriter, md);
    compiler.compile();
    CTCompiler.Output output = compiler.getOutput();
    CodeWriter code = new CodeWriter(classWriter);
    code.setMaxStack(output.getMaxStack());
    code.setMaxLocals(output.getMaxLocals());
    code.setCode(output.getCode());
    code.setRawHandlers(output.getRawHandlers());

    LineNumberTableWriter lines = null;
    //I guess it is the line numbers in the java files.
    if (rawLines == null) {
      // add fake line numbers: just map each bytecode instruction to its own
      // 'line'
      int[] newLineMap = new int[instructions.size()];
      for (int i = 0; i < newLineMap.length; i++) {
        newLineMap[i] = i;
      }
      int[] rawTable = LineNumberTableWriter.makeRawTable(newLineMap);
      lines = new LineNumberTableWriter(classWriter);
      lines.setRawTable(rawTable);
    }
    code.setAttributes(new ClassWriter.Element[] { rawLines == null ? lines : rawLines });
    Element[] elements = { code };
    classWriter.addMethod(access, name, sig, elements);
  }

  public void newMethod(MethodData md, ClassWriter classWriter, ClassWriter.Element rawLines) {
    CTCompiler compiler = new CTCompiler(classWriter, md);
    compiler.compile();
    CTCompiler.Output output = compiler.getOutput();
    CodeWriter code = new CodeWriter(classWriter);
    code.setMaxStack(output.getMaxStack());
    code.setMaxLocals(output.getMaxLocals());
    code.setCode(output.getCode());
    code.setRawHandlers(output.getRawHandlers());

    LineNumberTableWriter lines = null;
    //I guess it is the line numbers in the java files.
    if (rawLines == null) {
      // add fake line numbers: just map each bytecode instruction to its own
      // 'line'

      //NOTE:Should not use md.getInstructions().length, because the
      //the length of the created code can be smaller than the md's instruction
      // length

      //WRONG: int[] newLineMap = new int[md.getInstructions().length];
      int[] newLineMap = new int[code.getCodeLength()];
      for (int i = 0; i < newLineMap.length; i++) {
        newLineMap[i] = i;
      }
      int[] rawTable = LineNumberTableWriter.makeRawTable(newLineMap);
      lines = new LineNumberTableWriter(classWriter);
      lines.setRawTable(rawTable);
    }
    code.setAttributes(new ClassWriter.Element[] { rawLines == null ? lines : rawLines });
    Element[] elements = { code };
    //System.out.println("Name:"+md.getName()+" Sig:"+md.getSignature());
    classWriter.addMethod(md.getAccess(), md.getName(), md.getSignature(), elements);
  }

  /**
   * Do something to every method in the class. This will visit all methods,
   * including those already marked for deletion.
   * 
   * @param me
   *          the visitor to apply to each method
   */
  public void visitMethods(MethodExaminer me) throws InvalidClassFileException {
    for (int i = 0; i < methods.length; i++) {
      prepareMethod(i);
      if (methods[i] != null) {
        me.examineCode(methods[i]);
      }
    }
  }

  /**
   * Get the current state of method i. This can be edited using a MethodEditor.
   * 
   * @param i
   *          the index of the method to inspect
   */
  public MethodData visitMethod(int i) throws InvalidClassFileException {
    prepareMethod(i);
    return methods[i];
  }

  /**
   * Get the original code resource for the method.
   * 
   * @param i
   *          the index of the method to inspect
   */
  public CodeReader getMethodCode(int i) throws InvalidClassFileException {
    prepareMethod(i);
    return oldCode[i];
  }

  /**
   * Reset method i back to the code from the original class, and "undelete" it
   * if it was marked for deletion.
   * 
   * @param i
   *          the index of the method to reset
   */
  public void resetMethod(int i) {
    deletedMethods[i] = false;
    methods[i] = null;
  }

  /**
   * Replace the code for method i with new code. This also "undeletes" the
   * method if it was marked for deletion.
   * 
   * @param i
   *          the index of the method to replace
   */
  public void replaceMethod(int i, MethodData md) {
    deletedMethods[i] = false;
    methods[i] = md;
    oldCode[i] = null;
    md.setHasChanged();
  }

  /**
   * Check whether any methods in the class have actually been changed.
   */
  public boolean isChanged() {
    for (int i = 0; i < methods.length; i++) {
      if (deletedMethods[i] || (methods[i] != null && methods[i].getHasChanged())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Create a class which is a copy of the original class but with the new
   * method code. We return the ClassWriter used, so more methods and fields
   * (and other changes) can still be added.
   * 
   * We fix up any debug information to be consistent with the changes to the
   * code.
   */
  public ClassWriter emitClass() throws InvalidClassFileException {
    ClassWriter w = new ClassWriter();
    emitClassInto(w);
    return w;
  }

  /**
   * Copy the contents of the old class, plus any method modifications, into a
   * new ClassWriter. The ClassWriter must be empty!
   * 
   * @param w
   *          the classwriter to copy into.
   */
  private void emitClassInto(ClassWriter w) throws InvalidClassFileException {
    w.setMajorVersion(cr.getMajorVersion());
    w.setMinorVersion(cr.getMinorVersion());
    w.setRawCP(cr.getCP(), false);
    w.setAccessFlags(cr.getAccessFlags());
    w.setNameIndex(cr.getNameIndex());
    w.setSuperNameIndex(cr.getSuperNameIndex());
    w.setInterfaceNameIndices(cr.getInterfaceNameIndices());

    int fieldCount = cr.getFieldCount();
    for (int i = 0; i < fieldCount; i++) {
      w.addRawField(new ClassWriter.RawElement(cr.getBytes(), cr.getFieldRawOffset(i), cr.getFieldRawSize(i)));
    }

    for (int i = 0; i < methods.length; i++) {
      MethodData md = methods[i];
      if (!deletedMethods[i]) {
        if (md == null || !md.getHasChanged()) {
          w.addRawMethod(new ClassWriter.RawElement(cr.getBytes(), cr.getMethodRawOffset(i), cr.getMethodRawSize(i)));
        } else {
          CTCompiler comp = new CTCompiler(w, md);
          comp.setPresetConstants(cpr);

          try {
            comp.compile();
          } catch (Error ex) {
            ex.printStackTrace();
            throw new Error("Error compiling method " + md + ": " + ex.getMessage());
          } catch (Exception ex) {
            ex.printStackTrace();
            throw new Error("Error compiling method " + md + ": " + ex.getMessage());
          }

          CodeReader oc = oldCode[i];
          int flags = cr.getMethodAccessFlags(i);
          // we're not installing a native method here
          flags &= ~ClassConstants.ACC_NATIVE;
          w.addMethod(flags, cr.getMethodNameIndex(i), cr.getMethodTypeIndex(i), makeMethodAttributes(i, w, oc, comp.getOutput()));
          Compiler.Output[] aux = comp.getAuxiliaryMethods();
          if (aux != null) {
            for (int j = 0; j < aux.length; j++) {
              Compiler.Output a = aux[j];
              w.addMethod(a.getAccessFlags(), a.getMethodName(), a.getMethodSignature(), makeMethodAttributes(i, w, oc, a));
            }
          }
        }
      }
    }

    ClassReader.AttrIterator iter = new ClassReader.AttrIterator();
    cr.initClassAttributeIterator(iter);
    for (; iter.isValid(); iter.advance()) {
      w.addClassAttribute(new ClassWriter.RawElement(cr.getBytes(), iter.getRawOffset(), iter.getRawSize()));
    }
  }

  private static CodeWriter makeNewCode(ClassWriter w, Compiler.Output output) {
    CodeWriter code = new CodeWriter(w);
    code.setMaxStack(output.getMaxStack());
    code.setMaxLocals(output.getMaxLocals());
    code.setCode(output.getCode());
    code.setRawHandlers(output.getRawHandlers());
    return code;
  }

  private LineNumberTableWriter makeNewLines(ClassWriter w, CodeReader oldCode, Compiler.Output output)
      throws InvalidClassFileException {
    int[] newLineMap = null;
    int[] oldLineMap = LineNumberTableReader.makeBytecodeToSourceMap(oldCode);
    if (oldLineMap != null) {
      // Map the old line number map onto the new bytecodes
      int[] newToOldMap = output.getNewBytecodesToOldBytecodes();
      newLineMap = new int[newToOldMap.length];
      for (int i = 0; i < newToOldMap.length; i++) {
        int old = newToOldMap[i];
        if (old >= 0) {
          newLineMap[i] = oldLineMap[old];
        }
      }
    } else if (createFakeLineNumbers) {
      newLineMap = new int[output.getCode().length];
      for (int i = 0; i < newLineMap.length; i++) {
        newLineMap[i] = i + fakeLineOffset;
      }
    } else {
      return null;
    }

    // Now compress it into the JVM form
    int[] rawTable = LineNumberTableWriter.makeRawTable(newLineMap);
    if (rawTable == null || rawTable.length == 0) {
      return null;
    } else {
      LineNumberTableWriter lines = new LineNumberTableWriter(w);
      lines.setRawTable(rawTable);
      return lines;
    }
  }

  private static LocalVariableTableWriter makeNewLocals(ClassWriter w, CodeReader oldCode, Compiler.Output output)
      throws InvalidClassFileException {
    int[][] oldMap = LocalVariableTableReader.makeVarMap(oldCode);
    if (oldMap != null) {
      // Map the old map onto the new bytecodes
      int[] newToOldMap = output.getNewBytecodesToOldBytecodes();
      int[][] newMap = new int[newToOldMap.length][];
      int[] lastLocals = null;
      for (int i = 0; i < newToOldMap.length; i++) {
        int old = newToOldMap[i];
        if (old >= 0) {
          newMap[i] = oldMap[old];
          lastLocals = newMap[i];
        } else {
          newMap[i] = lastLocals;
        }
      }

      int[] rawTable = LocalVariableTableWriter.makeRawTable(newMap);
      if (rawTable == null || rawTable.length == 0) {
        return null;
      } else {
        LocalVariableTableWriter locals = new LocalVariableTableWriter(w);
        locals.setRawTable(rawTable);
        return locals;
      }
    } else {
      return null;
    }
  }

  private ClassWriter.Element[] makeMethodAttributes(int m, ClassWriter w, CodeReader oldCode, Compiler.Output output)
      throws InvalidClassFileException {
    CodeWriter code = makeNewCode(w, output);

    int codeAttrCount = 0;
    LineNumberTableWriter lines = null;
    LocalVariableTableWriter locals = null;
    if (oldCode != null) {
      lines = makeNewLines(w, oldCode, output);
      if (lines != null) {
        codeAttrCount++;
      }
      locals = makeNewLocals(w, oldCode, output);
      if (locals != null) {
        codeAttrCount++;
      }
    }
    ClassWriter.Element[] codeAttributes = new ClassWriter.Element[codeAttrCount];
    int codeAttrIndex = 0;
    if (lines != null) {
      codeAttributes[0] = lines;
      codeAttrIndex++;
    }
    if (locals != null) {
      codeAttributes[codeAttrIndex] = locals;
    }
    code.setAttributes(codeAttributes);

    ClassReader.AttrIterator iter = new ClassReader.AttrIterator();
    cr.initMethodAttributeIterator(m, iter);
    int methodAttrCount = iter.getRemainingAttributesCount();
    if (oldCode == null) {
      methodAttrCount++;
    }
    ClassWriter.Element[] methodAttributes = new ClassWriter.Element[methodAttrCount];
    for (int i = 0; iter.isValid(); iter.advance()) {
      if (iter.getName().equals("Code")) {
        methodAttributes[i] = code;
        code = null;
        if (oldCode == null) {
          throw new Error("No old code provided, but Code attribute found");
        }
      } else {
        methodAttributes[i] = new ClassWriter.RawElement(cr.getBytes(), iter.getRawOffset(), iter.getRawSize());
      }
      i++;
    }
    if (oldCode == null) {
      if (code == null) {
        throw new Error("Old code not provided but existing code was found and replaced");
      }
      methodAttributes[methodAttrCount - 1] = code;
    }

    return methodAttributes;
  }
}