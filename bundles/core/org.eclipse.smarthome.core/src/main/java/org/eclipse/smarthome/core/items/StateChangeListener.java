/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.smarthome.core.items;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.types.State;

/**
 * <p>
 * This interface must be implemented by all classes that want to be notified about changes in the state of an item.
 *
 * <p>
 * The {@link GenericItem} class provides the possibility to register such listeners.
 *
 * @author Kai Kreuzer - Initial contribution and API
 *
 */
@NonNullByDefault
public interface StateChangeListener {

    /**
     * This method is called, if a state has changed.
     *
     * @param item the item whose state has changed
     * @param oldState the previous state
     * @param newState the new state
     */
    public void stateChanged(Item item, State oldState, State newState);

    /**
     * This method is called, if a state was updated, whether or not it has changed
     *
     * @param item the item whose state was updated
     * @param oldState the previous state
     * @param newState the new state
     */
    public void stateUpdated(Item item, State oldState, State newState);

}
