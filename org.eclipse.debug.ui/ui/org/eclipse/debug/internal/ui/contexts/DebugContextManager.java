/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.contexts;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.ui.contexts.IDebugContextManager;
import org.eclipse.debug.ui.contexts.IDebugContextProvider;
import org.eclipse.debug.ui.contexts.IDebugContextService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * @since 3.2
 */
public class DebugContextManager implements IDebugContextManager {
	
	private static DebugContextManager fgDefault;
	private Map fServices = new HashMap();
	
	private DebugContextManager() {
	}
	
	public static IDebugContextManager getDefault() {
		if (fgDefault == null) {
			fgDefault = new DebugContextManager();
		}
		return fgDefault;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextManager#getDebugContextService(org.eclipse.ui.IWorkbenchWindow)
	 */
	public synchronized IDebugContextService getDebugContextService(IWorkbenchWindow window) {
		return createService(window);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextManager#addDebugContextProvider(org.eclipse.debug.ui.contexts.IDebugContextProvider)
	 */
	public void addDebugContextProvider(IDebugContextProvider provider) {
		IWorkbenchPart part = provider.getPart();
		IWorkbenchWindow window = part.getSite().getWorkbenchWindow();
		ContextService service = createService(window);
		service.addProvider(provider);
	}
	
	protected ContextService createService(IWorkbenchWindow window) {
		ContextService service = (ContextService) fServices.get(window);
		if (service == null) {
			service = new ContextService(window);
			fServices.put(window, service);
			// TODO: register 'null' provider (global)
		}
		return service;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.contexts.IDebugContextManager#removeDebugContextProvider(org.eclipse.debug.ui.contexts.IDebugContextProvider)
	 */
	public void removeDebugContextProvider(IDebugContextProvider provider) {
		IWorkbenchPart part = provider.getPart();
		IWorkbenchWindow window = part.getSite().getWorkbenchWindow();
		ContextService service = (ContextService) fServices.get(window);
		if (service != null) {
			service.removeProvider(provider);
		}
	}
	
}
