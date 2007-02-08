/******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.ipa.callgraph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.*;
import com.ibm.wala.util.Atom;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.warnings.WarningSet;

public class MiscellaneousHacksContextSelector implements ContextSelector 
{

  private final Set<MethodReference> methodsToSpecialize;

  private final ContextSelector specialPolicy;
  private final ContextSelector basePolicy;

  public MiscellaneousHacksContextSelector(ContextSelector special,
					   ContextSelector base,
					   ClassHierarchy cha,
					   String[][] descriptors)
  {
    basePolicy = base;
    specialPolicy = special;
    methodsToSpecialize = new HashSet<MethodReference>();
    for(int i = 0; i < descriptors.length; i++) {
      String[] descr = descriptors[i];
      switch (descr.length) {

      // loader name, classname, method name, method descr
      case 4: {
	MethodReference ref =
	  MethodReference.findOrCreate(
	    TypeReference.findOrCreate(
	      new ClassLoaderReference(
	        Atom.findOrCreateUnicodeAtom( descr[0] )),
	      TypeName.string2TypeName( descr[1] ) ),
	    Atom.findOrCreateUnicodeAtom( descr[2] ),
	    Descriptor.findOrCreateUTF8( descr[3] ));

	methodsToSpecialize.add( cha.resolveMethod( ref ).getReference() );
	break;
      }

      // classname, method name, method descr
      case 3: {
	MethodReference ref =
	  MethodReference.findOrCreate(
	    TypeReference.findOrCreate(
	      new ClassLoaderReference(
	        Atom.findOrCreateUnicodeAtom("Application")),
	      TypeName.string2TypeName( descr[0] ) ),
	    Atom.findOrCreateUnicodeAtom( descr[1] ),
	    Descriptor.findOrCreateUTF8( descr[2] ));

	methodsToSpecialize.add( cha.resolveMethod( ref ).getReference() );
	break;
      }

      // loader name, classname, meaning all methods of that class
      case 2: {
	IClass klass =
	  cha.lookupClass(
	    TypeReference.findOrCreate(
	      new ClassLoaderReference(
	        Atom.findOrCreateUnicodeAtom( descr[0] )),
	      TypeName.string2TypeName( descr[1] ) ) );

	for(Iterator M = klass.getDeclaredMethods().iterator(); M.hasNext(); ) {
	  methodsToSpecialize.add( ((IMethod)M.next()).getReference() );
	}

	break;
      }

      // classname, meaning all methods of that class
      case 1: {
	IClass klass =
	  cha.lookupClass(
	    TypeReference.findOrCreate(
	      new ClassLoaderReference(
	        Atom.findOrCreateUnicodeAtom("Application")),
	      TypeName.string2TypeName( descr[0] ) ) );

	for(Iterator M = klass.getDeclaredMethods().iterator(); M.hasNext(); ) {
	  methodsToSpecialize.add( ((IMethod)M.next()).getReference() );
	}

	break;
      }

      default: 
	Assertions.UNREACHABLE();
      }
    }
  }

  public Context getCalleeTarget(CGNode caller, CallSiteReference site, IMethod callee, InstanceKey receiver) {
    if (methodsToSpecialize.contains( callee.getReference() ))
      return specialPolicy.getCalleeTarget(caller, site, callee, receiver);
    else
      return basePolicy.getCalleeTarget(caller, site, callee, receiver);
  }

  /* (non-Javadoc)
   * @see com.ibm.wala.ipa.callgraph.ContextSelector#contextIsIrrelevant(com.ibm.wala.ipa.callgraph.CGNode, com.ibm.wala.classLoader.CallSiteReference)
   */
  public boolean contextIsIrrelevant(CGNode node, CallSiteReference site) {
    return basePolicy.contextIsIrrelevant(node,site);
  }

  public int getBoundOnNumberOfTargets(CGNode caller, CallSiteReference reference, IMethod targetMethod) {
    return -1;
  }

  public boolean mayUnderstand(CGNode caller, CallSiteReference site, IMethod targetMethod, InstanceKey instance) {
    return true;
  }

  public void setWarnings(WarningSet newWarnings) {
    // no-op, not bound to warnings
  }

  public boolean allSitesDispatchIdentically(CGNode node, CallSiteReference site) {
    return false;
  }
}
