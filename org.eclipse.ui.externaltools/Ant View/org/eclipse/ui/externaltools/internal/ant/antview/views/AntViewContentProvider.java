/*
 * Copyright (c) 2002, Roscoe Rush. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public License
 * Version 0.5 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.eclipse.org/
 *
 */
package org.eclipse.ui.externaltools.internal.ant.antview.views;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.eclipse.ant.core.AntRunner;
import org.eclipse.ant.core.TargetInfo;
import org.eclipse.core.internal.resources.File;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.externaltools.internal.ant.antview.core.IAntViewConstants;
import org.eclipse.ui.externaltools.internal.ant.antview.core.ResourceMgr;
import org.eclipse.ui.externaltools.internal.ant.antview.preferences.Preferences;
import org.eclipse.ui.externaltools.internal.ant.antview.tree.ElementNode;
import org.eclipse.ui.externaltools.internal.ant.antview.tree.ErrorNode;
import org.eclipse.ui.externaltools.internal.ant.antview.tree.ProjectErrorNode;
import org.eclipse.ui.externaltools.internal.ant.antview.tree.ProjectNode;
import org.eclipse.ui.externaltools.internal.ant.antview.tree.TargetNode;
import org.eclipse.ui.externaltools.internal.ant.antview.tree.TreeNode;
import org.eclipse.ui.externaltools.internal.ant.model.AntUtil;

public class AntViewContentProvider implements IStructuredContentProvider, ITreeContentProvider, IAntViewConstants, IResourceChangeListener {

	public static final String SEP_KEYVAL = "|";
	public static final String SEP_REC = ";";

	private TreeNode treeRoot = null;

	/**
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(Viewer, Object, Object)
	 */
	public void inputChanged(Viewer v, Object oldInput, Object newInput) {
	}
	/**
	 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
	 */
	public void dispose() {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		workspace.removeResourceChangeListener(this);
		saveTargetVector();
	}
	/**
	 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(Object)
	 */
	public Object[] getElements(Object parent) {
		if (parent.equals(ResourcesPlugin.getWorkspace())) {
			if (treeRoot == null)
				initialize();
			return getChildren(treeRoot);
		}
		if (parent instanceof TreeNode)
			return getChildren(parent);
		return new Object[0];
	}
	/**
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(Object)
	 */

	public Object getParent(Object child) {
		if (child instanceof TreeNode) {
			return ((TreeNode) child).getParent();
		}
		return null;
	}
	/**
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(Object)
	 */
	public Object[] getChildren(Object parent) {
		if (parent instanceof TreeNode) {
			if (((TreeNode) parent).hasChildren()) {
				return ((TreeNode) parent).getChildren();
			}
		}
		return new Object[0];
	}
	/**
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(Object)
	 */
	public boolean hasChildren(Object parent) {
		if (parent instanceof TreeNode)
			return ((TreeNode) parent).hasChildren();
		return false;
	}

	/**
	 * Method reset.
	 */
	public void reset() {
		saveTargetVector();
		clear();
		treeRoot = null;
	}
	/**
	 * Method clear.
	 */
	public void clear() {
		Vector targetVector = (Vector) treeRoot.getProperty("TargetVector");
		if (null == targetVector)
			return;
		Enumeration targets = targetVector.elements();
		while (targets.hasMoreElements()) {
			TreeNode item = (TreeNode) targets.nextElement();
			item.setSelected(false);
		}
		targetVector.removeAllElements();
	}
	/**
	 * Method getTargetVector.
	 * @return Vector
	 */
	public Vector getTargetVector() {
		return (Vector) treeRoot.getProperty("TargetVector");
	}
	/**
	 * Method initialize.
	 */
	private void initialize() {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		workspace.removeResourceChangeListener(this);

		treeRoot = new TreeNode("");
		treeRoot.setProperty("TargetVector", new Vector());

		final ArrayList buildFileList = new ArrayList();
		final String buildFileName = Preferences.getString(IAntViewConstants.PREF_ANT_BUILD_FILE);
		IResourceVisitor buildVisitor = new IResourceVisitor() {
			public boolean visit(IResource res) throws CoreException {
				if (res instanceof File) {
					if (res.getName().equalsIgnoreCase(buildFileName)) {
						IPath file = (IPath) ((IFile) res).getLocation();
						buildFileList.add(file);
					}
				}
				return true;
			}
		};

		try {
			IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
			workspaceRoot.accept(buildVisitor);
		} catch (CoreException e) {
			workspace.addResourceChangeListener(this);
			return;
		}

		if (0 == buildFileList.size()) {
			treeRoot.addChild(new ErrorNode(ResourceMgr.getString("Tree.NoProjects") + " " + "(" + Preferences.getString(PREF_ANT_BUILD_FILE) + ")"));
			workspace.addResourceChangeListener(this);
			return;
		}

		Iterator buildFiles = buildFileList.iterator();
		while (buildFiles.hasNext()) {
			IPath file = (IPath) buildFiles.next();
			treeRoot.addChild(parseAntBuildFile(file.toString()));
		}
		restoreTargetVector();
		workspace.addResourceChangeListener(this);
	}
	/**
	 * Method parseAntBuildFile.
	 * @param filename
	 * @return TreeNode
	 */
	private TreeNode parseAntBuildFile(String filename) {
		AntRunner runner = new AntRunner();
		runner.setBuildFileLocation(filename);
		TargetInfo[] infos = null;
		try {
			infos = runner.getAvailableTargets();
		} catch (CoreException e) {
			return new ProjectErrorNode(filename, "An exception occurred retrieving targets: " + e.getMessage());
		}
		if (infos.length < 1) {
			return new ProjectErrorNode(filename, "No targets found");
		}
		Project project = new Project();
		project.setName(infos[0].getProject());
		for (int i = 0; i < infos.length; i++) {
			TargetInfo info = infos[i];
			if (info.isDefault()) {
				project.setDefault(info.getName());
			}
			Target target = new Target();
			target.setName(info.getName());
			String[] dependencies = info.getDependencies();
			StringBuffer depends = new StringBuffer();
			int numDependencies= dependencies.length;
			if (numDependencies > 0) {
				// Onroll the loop to avoid trailing comma
				depends.append(dependencies[0]);
			}
			for (int j = 1; j < numDependencies; j++) {
				depends.append(',').append(dependencies[j]);
			}
			target.setDepends(depends.toString());
			target.setDescription(info.getDescription());
			project.addTarget(target);
		}
		if (project.getDefaultTarget() == null) {
			return new ProjectErrorNode(filename, ResourceMgr.getString("Tree.NoProjectElement"));
		}

		TreeNode projectNode = new ProjectNode(filename, project.getName());
		Enumeration projTargets = project.getTargets().elements();
		while (projTargets.hasMoreElements()) {
			Target target = (Target) projTargets.nextElement();
			// Target Node -----------------			
			TreeNode targetNode = new TargetNode(filename, target);
			projectNode.addChild(targetNode);
			// Dependency Sub-Node ---------
			TreeNode dependencyNode = new ElementNode(ResourceMgr.getString("Tree.Dependencies"));
			targetNode.addChild(dependencyNode);
			Enumeration dependency = target.getDependencies();
			while (dependency.hasMoreElements()) {
				dependencyNode.addChild(new ElementNode((String) dependency.nextElement()));
			}
			if (!dependencyNode.hasChildren()) {
				dependencyNode.addChild(new ElementNode(ResourceMgr.getString("Tree.None")));
			}
			// Execution Path Sub-Node -------
			TreeNode topoNode = new ElementNode(ResourceMgr.getString("Tree.ExecuteOrder"));
			targetNode.addChild(topoNode);
			Vector topoSort = project.topoSort(target.getName(), project.getTargets());
			int n = topoSort.indexOf(target) + 1;
			while (topoSort.size() > n)
				topoSort.remove(topoSort.size() - 1);
			topoSort.trimToSize();
			ListIterator topoElements = topoSort.listIterator();
			while (topoElements.hasNext()) {
				int i = topoElements.nextIndex();
				Target topoTask = (Target) topoElements.next();
				topoNode.addChild(new ElementNode((i + 1) + ":" + topoTask.getName()));
			}
		}
		return projectNode;
	}

	public void resourceChanged(IResourceChangeEvent event) {
		if (event.getType() != IResourceChangeEvent.POST_CHANGE)
			return;

		IResourceDelta delta = event.getDelta();
		if (delta == null)
			return;

		final ArrayList deltaResources = new ArrayList();
		final String buildFileName = Preferences.getString(IAntViewConstants.PREF_ANT_BUILD_FILE);
		IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {
			public boolean visit(IResourceDelta delta) {
				if (delta.getKind() != IResourceDelta.CHANGED)
					return true;
				if (0 == (delta.getFlags() & IResourceDelta.CONTENT))
					return true;
				IResource resource = delta.getResource();

				if (resource.getType() == IResource.FILE && buildFileName.equalsIgnoreCase(resource.getName())) {
					deltaResources.add(resource);
				}
				return true;
			}
		};

		try {
			delta.accept(visitor);
		} catch (CoreException e) {
			return;
		}

		if (0 == deltaResources.size())
			return;

		saveTargetVector();
		clear();
		Iterator changedResources = deltaResources.iterator();
		while (changedResources.hasNext()) {
			IResource fileResource = (IResource) changedResources.next();
			String buildFile = fileResource.getLocation().toString();
			TreeNode rootChild[] = treeRoot.getChildren();
			for (int i = 0; i < rootChild.length; i++) {
				String nodeBuildFile = (String) rootChild[i].getProperty("BuildFile");
				if (null == nodeBuildFile)
					continue;
				if (buildFile.equals(nodeBuildFile)) {
					treeRoot.removeChild(rootChild[i]);
					break;
				}
			}
			treeRoot.addChild(parseAntBuildFile(buildFile));
		}
		restoreTargetVector();
		AntView antView = AntUtil.getAntView();
		if (antView != null) {
			antView.refresh();
		}
	}

	private void saveTargetVector() {
		Vector targetVector = (Vector) treeRoot.getProperty("TargetVector");
		if (null == targetVector) {
			return;
		}
		String targets = "";
		ListIterator targetList = targetVector.listIterator();
		while (targetList.hasNext()) {
			TreeNode target = (TreeNode) targetList.next();
			targets += target.getProperty("BuildFile") + SEP_KEYVAL + target.getText() + SEP_REC;
		}
		Preferences.setString(PREF_TARGET_VECTOR, targets);
	}

	private void restoreTargetVector() {
		HashMap targetMap = new HashMap();
		TreeNode rootChildren[] = treeRoot.getChildren();
		for (int i = 0; i < rootChildren.length; i++) {
			TreeNode targetNodes[] = rootChildren[i].getChildren();
			for (int j = 0; j < targetNodes.length; j++) {
				targetMap.put(targetNodes[j].getProperty("BuildFile") + SEP_KEYVAL + targetNodes[j].getText(), targetNodes[j]);
			}
		}

		String targetsPref = Preferences.getString(PREF_TARGET_VECTOR);
		if (null == targetsPref || targetsPref.equals("")) {
			return;
		}
		String targets[] = split(targetsPref, SEP_REC);
		for (int i = 0; i < targets.length; i++) {
			if (null == targets[i] || targets[i].equals(""))
				continue;
			TreeNode targetNode = (TargetNode) targetMap.get(targets[i]);
			if (null == targetNode || targetNode.isSelected())
				continue;
			targetNode.setSelected(true);
			//-------------------
			//  	      TreeViewer viewer = AntviewPlugin.getDefault().getAntView().getTreeViewer();
			//  	      viewer.expandToLevel(targetNode, 2);
			//--------------------
		}
		AntView view = AntUtil.getAntView();
		if (view != null) {
			view.refresh();
		}
	}
	private String[] split(String string, String regExp) {
		List strings = new ArrayList();
		int start = string.indexOf(regExp);
		int stop = -1;
		while (start >= 0) {
			stop = string.indexOf(regExp, start + 1);
			if (stop > start) {
				strings.add(string.substring(start, stop));
			} else {
				strings.add(string.substring(start));
			}
		}
		String[] array = new String[strings.size()];
		strings.toArray(array);
		return array;
	}
}
