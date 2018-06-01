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

package com.mucommander.ui.action.impl;

import java.util.Map;

import com.mucommander.command.Command;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileOperation;
import com.mucommander.commons.file.filter.FileOperationFilter;
import com.mucommander.commons.file.impl.local.LocalFile;
import com.mucommander.job.TempOpenWithJob;
import com.mucommander.process.ProcessRunner;
import com.mucommander.utils.text.Translator;
import com.mucommander.ui.dialog.InformationDialog;
import com.mucommander.ui.dialog.file.ProgressDialog;
import com.mucommander.ui.main.MainFrame;

/**
 * Provides a common base for viewer and editor actions.
 * @author Maxence Bernard, Nicolas Rinaudo
 */
abstract class AbstractViewerAction extends SelectedFileAction {

    // - Initialization ------------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Creates a new instance of <code>AbstractViewerAction</code>.
     * @param mainFrame  frame to which the action is attached.
     * @param properties action's properties.
     */
    AbstractViewerAction(MainFrame mainFrame, Map<String, Object> properties) {
        super(mainFrame, properties);

        // Enable this action only if the currently selected file is can be read.
        setSelectedFileFilter(new FileOperationFilter(FileOperation.READ_FILE));
    }



    // - AbstractAction implementation ---------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Edits the currently selected file.
     */
    @Override
    public synchronized void performAction() {
        final AbstractFile file = mainFrame.getActiveTable().getSelectedFile(false, true);

        // At this stage, no assumption should be made on the type of file that is allowed to be viewed/edited:
        // viewer/editor implementations will decide whether they allow a particular file or not.
        if (file == null) {
            return;
        }
        Command customCommand = getCustomCommand(file);
        if (customCommand == null) {
            // If we're not using a custom editor, this action behaves exactly like its parent.
            performInternalAction(file);
            return;
        }
        // If it's local, init the custom editor on it.
        if (file.hasAncestor(LocalFile.class)) {
            try {
                ProcessRunner.execute(customCommand.getTokens(file), file);
            } catch(Exception e) {
                InformationDialog.showErrorDialog(mainFrame);
            }
        } else {
            // If it's distant, copies it locally before running the custom editor on it.
            ProgressDialog progressDialog = new ProgressDialog(mainFrame, Translator.get("copy_dialog.copying"));
            TempOpenWithJob job = new TempOpenWithJob(progressDialog, mainFrame, file, customCommand);
            progressDialog.start(job);
        }
    }



    // - Abstract methods ----------------------------------------------------------------
    // -----------------------------------------------------------------------------------
    /**
     * Opens the specified file without a custom command.
     * @param file file to open.
     */
    protected abstract void performInternalAction(AbstractFile file);

    protected abstract Command getCustomCommand(AbstractFile file);
}
