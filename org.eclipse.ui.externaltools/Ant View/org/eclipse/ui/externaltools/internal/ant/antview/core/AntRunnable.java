/*
 * Copyright (c) 2002, Roscoe Rush. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public License
 * Version 0.5 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.eclipse.org/
 *
 */
package org.eclipse.ui.externaltools.internal.ant.antview.core;

import java.lang.reflect.InvocationTargetException;
import java.util.ListIterator;
import java.util.Vector;

import org.apache.tools.ant.Project;
import org.eclipse.ant.core.AntRunner;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ui.externaltools.internal.ant.antview.preferences.Preferences;
import org.eclipse.ui.externaltools.internal.ant.antview.tree.TreeNode;
import org.eclipse.ui.externaltools.internal.ant.antview.views.AntView;
import org.eclipse.ui.externaltools.internal.ant.antview.views.AntViewContentProvider;
import org.eclipse.ui.externaltools.internal.ui.LogConsoleDocument;

public class AntRunnable implements IRunnableWithProgress {
	private static final String ANT_LOGGER_CLASS = "org.eclipse.ui.externaltools.internal.ant.logger.AntBuildLogger";
	private static final String INPUT_HANDLER_CLASS = "org.eclipse.ui.externaltools.internal.ant.inputhandler.AntInputHandler"; //$NON-NLS-1$
	private static final int TOTAL_WORK_UNITS = 100;
	
	private AntView antView;
	
	public AntRunnable(AntView antView) {
		this.antView= antView;
	}

	public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		monitor.beginTask(ResourceMgr.getString("Monitor.Title"), TOTAL_WORK_UNITS);

		AntViewContentProvider viewContentProvider = antView.getViewContentProvider();
		Vector targetVector = viewContentProvider.getTargetVector();

		if (0 == targetVector.size()) {
			LogConsoleDocument.getInstance().append(ResourceMgr.getString("Error.EmptyTargetVector") + "\n", LogConsoleDocument.MSG_ERR);
			monitor.done();
			return;
		}
		int workunit = TOTAL_WORK_UNITS / targetVector.size();
		if (0 == workunit)
			workunit = 1;
		ListIterator targets = targetVector.listIterator();
		while (targets.hasNext()) {
			TreeNode targetNode = (TreeNode) targets.next();

			String filename = (String) targetNode.getProperty("BuildFile");
			IPath path = new Path(filename);
			path = path.setDevice("");
			int trimCount = path.matchingFirstSegments(Platform.getLocation());
			if (trimCount > 0)
				path = path.removeFirstSegments(trimCount);
			path.removeLastSegments(1);

			AntRunner antRunner = new AntRunner();
			antRunner.setBuildFileLocation(filename);
			antRunner.addBuildLogger(ANT_LOGGER_CLASS);
			antRunner.setInputHandler(INPUT_HANDLER_CLASS);
			//             antRunner.addUserProperties();
			antRunner.setExecutionTargets(new String[] { targetNode.getText()});
			antRunner.setMessageOutputLevel(getAntDisplayLevel(Preferences.getString(IAntViewConstants.PREF_ANT_DISPLAY)));

			monitor.subTask(path.toString() + " -> " + targetNode.getText());
			try {
				antRunner.run(new SubProgressMonitor(monitor, workunit));
			} catch (CoreException e) {
				Throwable carriedException = e.getStatus().getException();
				if (carriedException instanceof OperationCanceledException) {
					throw new InterruptedException(carriedException.getMessage());
				} else {
					throw new InvocationTargetException(e);
				}
			} catch (OperationCanceledException e) {
				throw new InvocationTargetException(e);
			}
		}
		monitor.done();
	}

	private int getAntDisplayLevel(String level) {
		if (level.equals(IAntViewConstants.ANT_DISPLAYLVL_ERROR))
			return Project.MSG_ERR;
		if (level.equals(IAntViewConstants.ANT_DISPLAYLVL_WARN))
			return Project.MSG_WARN;
		if (level.equals(IAntViewConstants.ANT_DISPLAYLVL_INFO))
			return Project.MSG_INFO;
		if (level.equals(IAntViewConstants.ANT_DISPLAYLVL_VERBOSE))
			return Project.MSG_VERBOSE;
		if (level.equals(IAntViewConstants.ANT_DISPLAYLVL_DEBUG))
			return Project.MSG_DEBUG;
		return Project.MSG_DEBUG;
	}
}
