/*
 * Copyright (c) 2002, Roscoe Rush. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public License
 * Version 0.5 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.eclipse.org/
 *
 */
package org.eclipse.ui.externaltools.internal.ant.antview.tree;

import org.eclipse.swt.graphics.Image;

import org.eclipse.ui.externaltools.internal.ant.antview.core.ResourceMgr;


public class ElementNode extends TreeNode {
    public ElementNode(String text) { 
    	super(text);
    }
	public Image getImage() {		          		   
		if (hasChildren()) { 
		   return ResourceMgr.getImage(IMAGE_ELEMENTS);
		} else {
		   return ResourceMgr.getImage(IMAGE_ELEMENT);
		}
	}    
}
