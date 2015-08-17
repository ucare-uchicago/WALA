/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.viz.viewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.IR;

public class CgPanel extends JSplitPane{

  private final CallGraph cg;

  public CgPanel(CallGraph cg) {
    this.cg = cg;
    this.setDividerLocation(250);
    JTree tree = buildTree();
    this.setLeftComponent(new JScrollPane(tree));

    
    final IrAndSourceViewer irViewer = new IrAndSourceViewer(cg);
    this.setRightComponent(irViewer.getComponent());

    tree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath newLeadSelectionPath = e.getNewLeadSelectionPath();
        if (null == newLeadSelectionPath){
          return;
        }
        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) newLeadSelectionPath.getLastPathComponent();
        Object userObject = treeNode.getUserObject();
        if (userObject instanceof CGNode) {
          CGNode node = (CGNode) userObject;
          IR ir = node.getIR();
          irViewer.setIR(ir);
        } else if (userObject instanceof CallSiteReference){
          CGNode parentNode =  (CGNode) ((DefaultMutableTreeNode) treeNode.getParent()).getUserObject();
          IR ir = parentNode.getIR();
          irViewer.setIRAndPc(ir, ((CallSiteReference) userObject).getProgramCounter());
        }
      }
    });

  }

  private JTree buildTree() {

    CGNode cgRoot = cg.getFakeRootNode();
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(cgRoot);

    expandNode(root);
    JTree tree = new JTree(root);

    tree.addTreeExpansionListener(new TreeExpansionListener() {

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();
        if (path == null) {
          return;
        }
        DefaultMutableTreeNode lastNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        expandNode(lastNode);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {

      }
    });

    return tree;
  }

  private void expandNode(DefaultMutableTreeNode treeNode) {
    expandNode(treeNode, 3);
  }

  private void expandNode(DefaultMutableTreeNode treeNode, int rec) {
    if (rec == 0) {
      return;
    }

    Comparator<CallSiteReference> comparator = new Comparator<CallSiteReference>() {
      public int compare(CallSiteReference c1, CallSiteReference c2) {
        return c1.toString().compareTo(c2.toString()); // use your logic
      }
    };

    Comparator<CGNode> comparator2 = new Comparator<CGNode>() {
      public int compare(CGNode c1, CGNode c2) {
        return c1.toString().compareTo(c2.toString()); // use your logic
      }
    };

    if (treeNode.getChildCount() == 0) {
      List<DefaultMutableTreeNode> newChilds = new ArrayList<DefaultMutableTreeNode>();
      Object userObject = treeNode.getUserObject();
      if (userObject instanceof CGNode) {
        CGNode cgNode = (CGNode) userObject;

        ArrayList<CallSiteReference> cgl = new ArrayList<CallSiteReference>();
        for (Iterator<CallSiteReference> iter = cgNode.iterateCallSites(); iter.hasNext();) {
          CallSiteReference csr = iter.next();
          cgl.add(csr);
        }
        Collections.sort(cgl,comparator);

        for (Iterator<CallSiteReference> iter = cgl.iterator(); iter.hasNext();) {
          CallSiteReference csr = iter.next();
          newChilds.add(new DefaultMutableTreeNode(csr));
        }
      } else {
        assert userObject instanceof CallSiteReference;
        CallSiteReference csr = (CallSiteReference) userObject;
        CGNode cgNode = (CGNode) ((DefaultMutableTreeNode) treeNode.getParent()).getUserObject();
        Set<CGNode> successors = cg.getPossibleTargets(cgNode, csr);
        ArrayList<CGNode> cgl = new ArrayList<CGNode>(successors);
        Collections.sort(cgl, comparator2);
        for (CGNode successor : cgl) {
          newChilds.add(new DefaultMutableTreeNode(successor));
        }
      }

      for (DefaultMutableTreeNode newChild : newChilds) {
        treeNode.add(newChild);
      }
    }

    for (int i = 0; i < treeNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeNode.getChildAt(i);
      expandNode(child, rec - 1);
    }
  }

}
