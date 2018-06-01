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

package com.mucommander.ui.quicklist;

import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.quicklist.item.QuickListDataList;
import com.mucommander.ui.quicklist.item.QuickListDataModel;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * FileTablePopupWithDataList is a FileTablePopup which contains FileTablePopupDataList.
 * 
 * @author Arik Hadas
 */

public abstract class QuickListWithDataList<T> extends QuickList implements KeyListener {
	protected QuickListDataList<T> dataList;
	private QuickListWithEmptyMsg emptyPopup;


    private boolean supportDeleteItem;
	
	public QuickListWithDataList(QuickListContainer container, String header, String emptyPopupHeader) {
		super(container, header);

		// get the TablePopupDataList.
		dataList = getList();

		// add JScrollPane that contains the TablePopupDataList to the popup.
		JScrollPane scroll = new JScrollPane(dataList,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                // default we have quicklist height for 10 lines maximum
                // calculate new height to better filling
                if (container instanceof FolderPanel) {
                    FolderPanel folderPanel = (FolderPanel) container;
                    Dimension parentSize = folderPanel.getSize();
                    Dimension preferredSize = dataList.getPreferredSize();
                    return new Dimension(super.getPreferredSize().width,
                            preferredSize.height < parentSize.height*8/10 ? preferredSize.height : parentSize.height*8/10);
                }
                return super.getPreferredSize();
            }
        };
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setFocusable(false);
        scroll.getHorizontalScrollBar().setFocusable(false);
        add(scroll);
        
        dataList.addFocusListener(this);
        
        // create TablePopupWithEmptyMsg that will be shown instead of this popup, if this
        // popup's data list won't have any elements.
        emptyPopup = new QuickListWithEmptyMsg(container, header, emptyPopupHeader);
        dataList.addKeyListener(this);
	}
	
	protected abstract T[] getData();
	
	/**
	 * This function will be called when an element from the data list will be selected.
	 * 
	 * @param item - The selected item from the data list.
	 */
	public void itemSelected(T item) {
		setVisible(false);
		acceptListItem(item);
	}		
	
	@Override
    protected boolean prepareForShowing(QuickListContainer container) {
		boolean toShow = false;
		// if data list contains at least 1 element, show this popup.
		T[] data = getData();
		if (data.length > 0) {
			dataList.setListData(data);
			toShow = true;
		} else {        // else, show popup with a "no elements" message.
            emptyPopup.show();
        }
		
		return toShow;
	}

	@Override
	protected void getFocus() {
		// to overcome #552 (right recentQL not focused) both must be used:
		// invokeLater and requestFocus (requestFocusInWindow is not sufficient)
		SwingUtilities.invokeLater(dataList::requestFocus);
	}
	
	/**
	 * This function defines what should be done with a selected item from the data list.
	 * 
	 * @param item - The selected item from the data list.
	 */
	protected abstract void acceptListItem(T item);
	
	protected abstract QuickListDataList<T> getList();

    protected void deleteListItem(int index, T item) {
        if (!supportDeleteItem) {
            return;
        }
        onDeleteItem(index, item);
        QuickListDataModel model = (QuickListDataModel)dataList.getModel();
        model.remove(index);

        int selectedIndex = index;
        if (selectedIndex >= model.getSize()) {
            selectedIndex--;
        }
        dataList.setSelectedIndex(selectedIndex);
        dataList.repaint();
    }

    protected void onDeleteItem(int index, T item) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (dataList == null) {
            return;
        }
        int selectedIndex = dataList.getSelectedIndex();
        int lastIndex = dataList.getModel().getSize() - 1;
        if (e.getExtendedKeyCode() == KeyEvent.VK_UP && selectedIndex == 0) {
            dataList.setSelectedIndex(lastIndex);
            dataList.ensureIndexIsVisible(lastIndex);
            e.consume();
        } else if (e.getExtendedKeyCode() == KeyEvent.VK_DOWN && dataList.getSelectedIndex() == lastIndex) {
            dataList.setSelectedIndex(0);
            dataList.ensureIndexIsVisible(0);
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (supportDeleteItem && e.getKeyCode() == KeyEvent.VK_DELETE) {
            int index = dataList.getSelectedIndex();
            if (index >= 0 && dataList.getModel().getSize() > 0) {
                deleteListItem(index, dataList.getSelectedValue());
            }
        }
    }

    protected boolean isSupportDeleteItem() {
        return supportDeleteItem;
    }

    protected void setSupportDeleteItem(boolean supportDeleteItem) {
        this.supportDeleteItem = supportDeleteItem;
    }

}
