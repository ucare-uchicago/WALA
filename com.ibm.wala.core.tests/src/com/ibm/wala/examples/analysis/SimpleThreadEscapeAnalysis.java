/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.examples.analysis;

import com.ibm.wala.classLoader.*;
import com.ibm.wala.client.impl.*;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.*;
import com.ibm.wala.ipa.callgraph.propagation.*;
import com.ibm.wala.ipa.cha.*;
import com.ibm.wala.types.*;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.intset.*;

import java.io.*;
import java.util.*;
import java.util.jar.*;

/**
 * <P>
 * A simple thread-level escape analysis: this code computes the set of classes
 * of which some instance may be accessed by some thread other than the one that
 * created it.
 * </P>
 * 
 * <P>
 * The algorithm is not very bright; it is based on the observation that there
 * are only three ways for an object to pass from one thread to another.
 * <UL>
 * <LI> The object is stored into a static variable.
 * <LI> The object is stored into an instance field of a Thread
 * <LI> The object is reachable from a field of another escaping object.
 * </UL>
 * </P>
 * 
 * <P>
 * This observation is implemented in the obvious way:
 * <OL>
 *  <LI> All static fields are collected
 *  <LI> All Thread constructor parameters are collected
 *  <LI> The points-to sets of these values represent the base set of escapees.
 *  <LI> All object reachable from fields of these objects are added
 *  <LI> This process continues until a fixpoint is reached
 *  <LI> The abstract objects in the points-to sets are converted to types
 *  <LI> This set of types is returned
 * </OL></P>
 *
 * @author Julian Dolby
 */
public class SimpleThreadEscapeAnalysis extends AbstractAnalysisEngine {
  private final Set<JarFile> applicationJarFiles;

  private final String applicationMainClass;

  /**
   * The two input parameters define the program to analyze: the jars of .class
   * files and the main class to start from.
   */
  public SimpleThreadEscapeAnalysis(Set<JarFile> applicationJarFiles, String applicationMainClass) {
    this.applicationJarFiles = applicationJarFiles;
    this.applicationMainClass = applicationMainClass;
  }

  /**
   * Given a root path, add it to the set if it is a jar, or traverse it
   * recursively if it is a directory.
   */
  private void collectJars(File f, Set<JarFile> result) throws IOException {
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      for (int i = 0; i < files.length; i++) {
        collectJars(files[i], result);
      }
    } else if (f.getAbsolutePath().endsWith(".jar")) {
      result.add(new JarFile(f));
    }
  }

  /**
   * Collect the set of JarFiles that constitute the system libraries of the
   * running JRE.
   */
  private JarFile[] getSystemJars() throws IOException {
    Set<JarFile> jarFiles = HashSetFactory.make();

    String javaHomePath = System.getProperty("java.home");

    if (!javaHomePath.endsWith(File.separator)) {
      javaHomePath = javaHomePath + File.separator;
    }

    collectJars(new File(javaHomePath + "lib"), jarFiles);

    return jarFiles.toArray(new JarFile[jarFiles.size()]);
  }

  /**
   * Take the given set of JarFiles that constitute the program, and return a
   * set of Module files as expected by the WALA machinery.
   */
  private Set<JarFileModule> getModuleFiles() {
    Set<JarFileModule> result = HashSetFactory.make();
    for (Iterator<JarFile> jars = applicationJarFiles.iterator(); jars.hasNext();) {
      result.add(new JarFileModule(jars.next()));
    }

    return result;
  }

  /**
   * The heart of the analysis.
   */
  public Set<IClass> gatherThreadEscapingClasses() throws IOException, ClassHierarchyException {

    //
    // set the application to analyze
    //
    setModuleFiles(getModuleFiles());

    //
    // set the system jar files to use.
    // change this if you want to use a specific jre version
    //
    setJ2SELibraries(getSystemJars());

    //
    // the application and libraries are set, now build the scope...
    //
    buildAnalysisScope();

    //
    // ...and the class hierarchy
    //
    ClassHierarchy cha = buildClassHierarchy();
    setClassHierarchy(cha);

    //
    // select the call graph construction algorithm
    // change this if greater precision is desired
    // (see com.ibm.wala.client.impl.*BuilderFactory)
    //
    setCallGraphBuilderFactory(new ZeroCFABuilderFactory());

    //
    // entrypoints are where analysis starts
    //
    Entrypoints roots = Util.makeMainEntrypoints(getScope(), cha, applicationMainClass);

    //
    // analysis options controls aspects of call graph construction
    //
    AnalysisOptions options = getDefaultOptions(roots);

    //
    // build the call graph
    //
    buildCallGraph(cha, options, true);

    //
    // extract data for analysis
    //
    CallGraph cg = getCallGraph();
    PointerAnalysis pa = getPointerAnalysis();

    //
    // collect all places where objects can escape their creating thread:
    // 1) all static fields
    // 2) arguments to Thread constructors
    //
    Set<PointerKey> escapeAnalysisRoots = HashSetFactory.make();
    HeapModel heapModel = pa.getHeapModel();

    // 1) static fields
    for (Iterator<IClass> clss = cha.iterateAllClasses(); clss.hasNext();) {
      IClass cls = (IClass) clss.next();
      Collection<IField> staticFields = cls.getDeclaredStaticFields();
      for (Iterator<IField> sfs = staticFields.iterator(); sfs.hasNext();) {
        IField sf = (IField) sfs.next();
        if (sf.getFieldTypeReference().isReferenceType()) {
          escapeAnalysisRoots.add(heapModel.getPointerKeyForStaticField(sf));
        }
      }
    }

    // 2) instance fields of Threads
    // (we hack this by getting the 'this' parameter of all ctor calls;
    // this works because the next phase will add all objects transitively
    // reachable from fields of types in these pointer keys, and all
    // Thread objects must be constructed somewhere)
    Collection<IClass> threads = cha.computeSubClasses(TypeReference.JavaLangThread);
    for (Iterator<IClass>clss = threads.iterator(); clss.hasNext();) {
      IClass cls = (IClass) clss.next();
      for (Iterator<IMethod> ms = cls.getDeclaredMethods(); ms.hasNext();) {
        IMethod m = (IMethod) ms.next();
        if (m.isInit()) {
          Set<CGNode> nodes = cg.getNodes(m.getReference());
          for (Iterator<CGNode> ns = nodes.iterator(); ns.hasNext();) {
            CGNode n = (CGNode) ns.next();
            escapeAnalysisRoots.add(heapModel.getPointerKeyForLocal(n, 1));
          }
        }
      }
    }

    // 
    // compute escaping types: all types flowing to escaping roots and
    // all types transitively reachable through their fields.
    //
    Set<InstanceKey> escapingInstanceKeys = HashSetFactory.make();

    //
    // pass 1: get abstract objects (instance keys) for escaping locations
    //
    for (Iterator<PointerKey> rts = escapeAnalysisRoots.iterator(); rts.hasNext();) {
      PointerKey root = rts.next();
      OrdinalSet<InstanceKey> objects = pa.getPointsToSet(root);
      for (Iterator<InstanceKey> objs = objects.iterator(); objs.hasNext();) {
        InstanceKey obj = (InstanceKey) objs.next();
        escapingInstanceKeys.add(obj);
      }
    }

    //
    // passes 2+: get fields of escaping keys, and add pointed-to keys
    //
    Set<InstanceKey> newKeys = HashSetFactory.make();
    do {
      newKeys.clear();
      for (Iterator<InstanceKey> keys = escapingInstanceKeys.iterator(); keys.hasNext();) {
        InstanceKey key = keys.next();
        IClass type = key.getConcreteType();
        if (type.isReferenceType()) {
          if (type.isArrayClass()) {
            if (((ArrayClass) type).getElementClass() != null) {
              PointerKey fk = heapModel.getPointerKeyForArrayContents(key);
              OrdinalSet<InstanceKey> fobjects = pa.getPointsToSet(fk);
              for (Iterator<InstanceKey> fobjs = fobjects.iterator(); fobjs.hasNext();) {
                InstanceKey fobj = (InstanceKey) fobjs.next();
                if (!escapingInstanceKeys.contains(fobj)) {
                  newKeys.add(fobj);
                }
              }
            }
          } else {
            Collection<IField> fields = type.getAllInstanceFields();
            for (Iterator<IField> fs = fields.iterator(); fs.hasNext();) {
              IField f = (IField) fs.next();
              if (f.getFieldTypeReference().isReferenceType()) {
                PointerKey fk = heapModel.getPointerKeyForInstanceField(key, f);
                OrdinalSet<InstanceKey> fobjects = pa.getPointsToSet(fk);
                for (Iterator<InstanceKey> fobjs = fobjects.iterator(); fobjs.hasNext();) {
                  InstanceKey fobj = (InstanceKey) fobjs.next();
                  if (!escapingInstanceKeys.contains(fobj)) {
                    newKeys.add(fobj);
                  }
                }
              }
            }
          }
        }
      }
      escapingInstanceKeys.addAll(newKeys);
    } while (!newKeys.isEmpty());

    //
    // get set of types from set of instance keys
    //
    Set<IClass> escapingTypes = HashSetFactory.make();
    for (Iterator<InstanceKey> keys = escapingInstanceKeys.iterator(); keys.hasNext();) {
      InstanceKey key = keys.next();
      escapingTypes.add(key.getConcreteType());
    }

    return escapingTypes;
  }

  public static void main(String[] args) throws IOException, ClassHierarchyException {
    String mainClassName = args[0];

    Set<JarFile> jars = HashSetFactory.make();
    for (int i = 1; i < args.length; i++) {
      jars.add(new JarFile(args[i]));
    }

    Set<IClass> escapingTypes = (new SimpleThreadEscapeAnalysis(jars, mainClassName)).gatherThreadEscapingClasses();

    for (Iterator<IClass> types = escapingTypes.iterator(); types.hasNext();) {
      System.out.println(types.next().getName().toString());
    }
  }
}
