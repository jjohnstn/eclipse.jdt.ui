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
package org.eclipse.ltk.core.refactoring.participants;

import org.eclipse.ltk.internal.core.refactoring.Assert;

/**
 * A generic delete refactoring. The actual refactoring is done
 * by the delete processor passed to the constructor.
 * <p>
 * This class is not intended to be subclassed by clients.
 * </p>
 * @since 3.0
 */
public class DeleteRefactoring extends ProcessorBasedRefactoring {

	private DeleteProcessor fProcessor;
	
	/**
	 * Constructs a new delete refactoring for the given processor.
	 * 
	 * @param processor the delete processor
	 */
	public DeleteRefactoring(DeleteProcessor processor) {
		Assert.isNotNull(processor);
		fProcessor= processor;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public RefactoringProcessor getProcessor() {
		return fProcessor;
	}
}
