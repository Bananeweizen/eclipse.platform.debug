/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.expression;

 
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.views.AbstractDebugEventHandler;
import org.eclipse.debug.internal.ui.views.variables.VariablesView;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
 
/**
 * Displays expressions and their values with a detail
 * area.
 */
public class ExpressionView extends VariablesView {	
	/**
	 * Creates this view's content provider.
	 * 
	 * @return a content provider
	 */
	protected IContentProvider createContentProvider() {
		return new ExpressionViewContentProvider();
	}
	
	/**
	 * Creates this view's event handler.
	 * 
	 * @param viewer the viewer associated with this view
	 * @return an event handler
	 */
	protected AbstractDebugEventHandler createEventHandler(Viewer viewer) {
		return new ExpressionViewEventHandler(this);
	}		
	
	/**
	 * @see AbstractDebugView#getHelpContextId()
	 */
	protected String getHelpContextId() {
		return IDebugHelpContextIds.EXPRESSION_VIEW;		
	}	
	
	/**
	 * Initializes the viewer input on creation
	 */
	protected void setInitialContent() {
		getViewer().setInput(DebugPlugin.getDefault().getExpressionManager());
	}	
	
	/**
	 * Configures the toolBar.
	 * 
	 * @param tbm The toolbar that will be configured
	 */
	protected void configureToolBar(IToolBarManager tbm) {
		tbm.add(new Separator(this.getClass().getName()));
		tbm.add(new Separator(IDebugUIConstants.RENDER_GROUP));
		tbm.add(getAction("ShowTypeNames")); //$NON-NLS-1$
		tbm.add(new Separator(IDebugUIConstants.EMPTY_EXPRESSION_GROUP));		
		tbm.add(new Separator(IDebugUIConstants.EXPRESSION_GROUP));
		tbm.add(new Separator("TOGGLE_VIEW")); //$NON-NLS-1$
		tbm.add(getAction("ShowDetailPane")); //$NON-NLS-1$
	}	
	
   /**
	* Adds items to the tree viewer's context menu including any extension defined
	* actions.
	* 
	* @param menu The menu to add the item to.
	*/
	protected void fillContextMenu(IMenuManager menu) {

		menu.add(new Separator(IDebugUIConstants.EMPTY_EXPRESSION_GROUP));
		menu.add(new Separator(IDebugUIConstants.EXPRESSION_GROUP));
		menu.add(getAction("ChangeVariableValue")); //$NON-NLS-1$
		menu.add(new Separator(IDebugUIConstants.EMPTY_RENDER_GROUP));
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	/** 
	 * The <code>ExpressionView</code> listens for selection changes in the <code>LaunchesView</code>
	 * to correctly set the editable state of the details pane.
	 *
	 * @see ISelectionListener#selectionChanged(IWorkbenchPart, ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		updateAction("ContentAssist"); //$NON-NLS-1$
	}
	
	/**
	 * Do nothing - the input to this view never changes - 
	 * it is always the expression manager.
	 * 
	 * @see VariablesView#setViewerInput(IStructuredSelection)
	 */
	protected void setViewerInput(IStructuredSelection ssel) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.variables.VariablesView#treeSelectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent)
	 */
	protected void treeSelectionChanged(SelectionChangedEvent event) {
		super.treeSelectionChanged(event);
		ISelection selection = event.getSelection();
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ssel= (IStructuredSelection)selection;
			if (ssel.size() == 1) {
				Object input= ssel.getFirstElement();
				if (input instanceof IDebugElement) {
					getDetailViewer().setEditable(true);
					return;
				} 
			}
		}
		getDetailViewer().setEditable(false);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.AbstractDebugView#createActions()
	 */
	protected void createActions() {
		super.createActions();
		setInitialContent();
	}

}
