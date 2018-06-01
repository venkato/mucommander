/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2012 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.ui.dialog.pref.component;

import com.mucommander.ui.combobox.MuComboBox;
import com.mucommander.ui.dialog.pref.PreferencesDialog;

import java.util.List;

/**
 * @author Arik Hadas, Oleg Trifonov
 */
public abstract class PrefComboBox<E> extends MuComboBox<E> implements PrefComponent {

	protected PrefComboBox() {
		super();
	}

    protected PrefComboBox(List<E> items) {
        super();
        addItems(items);
    }

    protected PrefComboBox(E[] items) {
        super();
        addItems(items);
    }

    private void addItems(List<E> items) {
        for (E item : items) {
            addItem(item);
        }
    }

    private void addItems(E[] items) {
        for (E item : items) {
            addItem(item);
        }
    }
	
	public void addDialogListener(final PreferencesDialog dialog) {
		addItemListener(e -> dialog.componentChanged(PrefComboBox.this));
	}

    @Override
    public E getSelectedItem() {
        return (E) super.getSelectedItem();
    }

}
