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


package com.mucommander.job;

import java.util.WeakHashMap;

import com.mucommander.ui.dialog.PasswordDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.impl.CachedFile;
import com.mucommander.commons.file.util.FileSet;
import com.mucommander.job.progress.JobProgress;
import com.mucommander.job.ui.DialogResult;
import com.mucommander.job.ui.UserInputHelper;
import com.mucommander.utils.text.Translator;
import com.mucommander.ui.dialog.QuestionDialog;
import com.mucommander.ui.dialog.file.ProgressDialog;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.ui.main.table.FileTable;
import com.mucommander.ui.notifier.AbstractNotifier;
import com.mucommander.ui.notifier.NotificationType;


/**
 * FileJob is a container for a 'file getTask' : basically an operation that involves files and bytes.
 * The class extending FileJob is required to give some information about the status of the job that
 * will be used to display visual indications of the job's progress.
 * <p>
 * The actual processing is performed in a separate thread. A FileJob needs to be started explicitly using
 * {@link #start()}. The lifecycle of a FileJob is as follows:<br>
 * <br>
 * <pre>
 * {@link State#NOT_STARTED} -&gt; {@link State#RUNNING} -&gt; {@link State#FINISHED}
 *                         ^                |
 *                         |                -&gt; {@link State#INTERRUPTED}
 *                         |                |                      
 *                         |                -&gt; {@link State#PAUSED} -|
 *                         |                                    |
 *                         -------------------------------------|
 * </pre>
 *
 * @author Maxence Bernard
 */
public abstract class FileJob implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileJob.class);

    /** Thread in which the file job is performed */
    private Thread jobThread;

    /** Lock used when job is being paused */
    private final Object pauseLock = new Object();

    /** Timestamp in milliseconds when job started */
    private long startDate;

    /** Timestamp in milliseconds when job has finished */
    private long endDate;

    /** Number of milliseconds during which this job has been paused (been waiting for some user response).
     * Used to compute stats like average speed. */
    private long pausedTime;

    /** Contains the timestamp when this job has been put in pause (if in pause) */
    private long pauseStartDate;

    /** Associated dialog showing job progression */
    private ProgressDialog progressDialog;

    /** Main frame on which the job is to be performed */ 
    private MainFrame mainFrame;
	
    /** Base source folder */
    private AbstractFile baseSourceFolder;
	
    /** Files which are going to be processed */
    protected FileSet files;

    /** Number of files that this job contains */
    private int nbFiles;

    /** Index of file currently being processed, see {@link #getCurrentFileIndex()} */
    private int currentFileIndex = -1;

    /** File currently being processed */
    private AbstractFile currentFile;

    /** Name of the file currently being processed */
    private String currentFilename = "";

    /** If set to true, processed files will be unmarked from current table */
    private boolean autoUnmark = true;
	
    /** File to be selected after job has finished (can be null if not set) */
    private AbstractFile fileToSelect;

    public enum State {
    
        /**
         * Indicates that this job has not started yet, this is a temporary state
         */
        NOT_STARTED,

        /**
         * Indicates that this job is currently processing files, this is a temporary state
         */
        RUNNING,

        /**
         * Indicates that this job is currently paused, waiting for user response, this is a temporary state
         */
        PAUSED,

        /**
         * Indicates that this job has been interrupted by the end user, this is a permanent state
         */
        INTERRUPTED,

        /** Indicates that this job has naturally finished (i.e. without being interrupted), this is a permanent state */
        FINISHED
    }


    /** Current state of this job */
    private State jobState = State.NOT_STARTED;

    /** List of registered FileJobListener stored as weak references */
    private WeakHashMap<FileJobListener, ?> listeners = new WeakHashMap<>();
    
    /** Information about this job progress */
    private JobProgress jobProgress;

    /** True if the user asked to automatically skip errors */
    private boolean autoSkipErrors;

//    private int nbFilesProcessed;
//    private int nbFilesDiscovered;

    protected final static int SKIP_ACTION = 0;
    protected final static int SKIP_ALL_ACTION = 1;
    protected final static int RETRY_ACTION = 2;
    protected final static int CANCEL_ACTION = 3;
    protected final static int APPEND_ACTION = 4;
    protected final static int OK_ACTION = 5;
    protected final static int OVERWRITE_READONLY_ACTION = 6;
    protected final static int OVERWRITE_READONLY_ALL_ACTION = 7;
    protected final static int RETRY_AS_ROOT_ACTION = 8;
    protected final static int RETRY_AS_ROOT_ALWAYS_ACTION = 9;

    protected final static String SKIP_TEXT = Translator.get("skip");
    protected final static String SKIP_ALL_TEXT = Translator.get("skip_all");
    protected final static String RETRY_TEXT = Translator.get("retry");
    protected final static String RETRY_AS_ROOT_TEXT = Translator.get("retry_as_root");
    protected final static String RETRY_AS_ROOT_ALWAYS_TEXT = Translator.get("retry_as_root_always");
    protected final static String CANCEL_TEXT = Translator.get("cancel");
    protected final static String APPEND_TEXT = Translator.get("resume");
    protected final static String OK_TEXT = Translator.get("ok");
    protected final static String OVERWRITE_READONLY_TEXT = Translator.get("overwrite");
    protected final static String OVERWRITE_READONLY_ALL_TEXT = Translator.get("overwrite_all");


    /**
     * Creates a new FileJob without starting it.
     *
     * @param progressDialog dialog which shows this job's progress
     * @param mainFrame mainFrame this job has been triggered by
     * @param files files which are going to be processed
     */
    public FileJob(ProgressDialog progressDialog, MainFrame mainFrame, FileSet files) {
        this(mainFrame, files);
        this.progressDialog = progressDialog;
    }

	
    /**
     * Creates a new FileJob without starting it, and with no associated ProgressDialog.
     *
     * @param mainFrame mainFrame this job has been triggered by
     * @param files files which are going to be processed
     */
    public FileJob(MainFrame mainFrame, FileSet files) {
        this.mainFrame = mainFrame;
        setFiles(files);
    	this.jobProgress = new JobProgress(this);
    }

    public FileJob(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.jobProgress = new JobProgress(this);
    }
	
	
    /**
     * Specifies whether or not files that have been processed should be unmarked from current table (enabled by default).
     *
     * @param autoUnmark <code>true</code> to automatically unmark files after they have been processed.
     */
    public void setAutoUnmark(boolean autoUnmark) {
        this.autoUnmark = autoUnmark;
    }

    /**
     * Sets whether or not this file job should automatically skip errors when encountered (disabled by default).
     *
     * @param autoSkipErrors <code>true</code> to automatically skip errors, <code>false</code> to show an error dialog.
     */
    public void setAutoSkipErrors(boolean autoSkipErrors) {
        this.autoSkipErrors = autoSkipErrors;
    }

	
    /**
     * Sets the given file to be selected in the active table after this job has finished.
     * The file will only be selected if it exists in the active table's folder and if this job hasn't
     * been cancelled. The selection will occur after the tables have been refreshed (if they are refreshed).
     *
     * @param file the file to be selected in the active table after this job has finished
     */
    protected void selectFileWhenFinished(AbstractFile file) {
        this.fileToSelect = file;
    }
	
	
    /**
     * Starts file job in a separate thread.
     */
    public void start() {
        // Return if job has already been started
        if (getState() != State.NOT_STARTED) {
            return;
        }

        // Pause auto-refresh during file job as it potentially modifies the current folders contents
        // and would potentially cause folder panel to auto-refresh
        getMainFrame().getLeftPanel().getFolderChangeMonitor().setPaused(true);
        getMainFrame().getRightPanel().getFolderChangeMonitor().setPaused(true);

        setState(State.RUNNING);
        startDate = System.currentTimeMillis();

        jobThread = new Thread(this, getClass().getName());
        jobThread.start();
    }


	/**
	 * Returns the dialog showing progress of this job.
	 * @return the progressDialog
	 */
	protected ProgressDialog getProgressDialog() {
		return progressDialog;
	}


	/**
	 * Returns the main frame.
	 * @return the mainFrame
	 */
	protected MainFrame getMainFrame() {
		return mainFrame;
	}


	/**
     * Returns the current state of this FileJob. See constant fields for possible return values.
     *
     * @return the current state of this FileJob. See constant fields for possible return values.
     */
    public State getState() {
        return jobState;
    }

    /**
     * Sets a new state for this FileJob and notifies registered FileJobListener instances of the change.
     *
     * @param jobState the new state
     */
    protected void setState(State jobState) {
        State oldState = this.jobState;
        this.jobState = jobState;

        for (FileJobListener listener : listeners.keySet())
            listener.jobStateChanged(this, oldState, jobState);
    }


    /**
     * Registers a FileJobListener to receive notifications whenever state of this FileJob changes.
     *
     * <p>Listeners are stored as weak references so {@link #removeFileJobListener(FileJobListener)}
     * doesn't need to be called for listeners to be garbage collected when they're not used anymore.
     *
     * @param listener the FileJobListener to register
     */
    public void addFileJobListener(FileJobListener listener) {
        listeners.put(listener, null);
    }

    /**
     * Removes the given FileJobListener from the list of listeners that receive notifications when the state of
     * this FileJob has changed.
     *
     * @param listener the FileJobListener to remove
     */
    public void removeFileJobListener(FileJobListener listener) {
        listeners.remove(listener);
    }


    /**
     * Returns the timestamp in milliseconds when this job started.
     *
     * @return the timestamp in milliseconds when this job started
     */
    public long getStartDate() {
        return startDate;
    }

    /**
     * Returns the timestamp in milliseconds when this job ended, <code>0</code> if this job hasn't finished yet.
     *
     * @return the timestamp in milliseconds when this job ended
     */
    public long getEndDate() {
        return endDate;
    }

    /**
     * Returns the timestamp in milliseconds when this job was last paused, <code>0</code> if this job has not been
     * paused yet.
     *
     * @return the timestamp in milliseconds when this job was last paused
     */
    public long getPauseStartDate() {
        return pauseStartDate;
    }
    
    /**
     * Sets the timestamp in milliseconds when this job is paused.
     */
    private void setPauseStartDate() {
        this.pauseStartDate = System.currentTimeMillis();
    }

    
    /**
     * Returns the number of milliseconds during which this job has been paused (been waiting for some user response).
     * If this job has been paused several times, the total is returned. If this job has not been paused yet,
     * <code>0</code> is returned.
     *
     * @return the number of milliseconds during which this job has been paused
     */
    public long getPausedTime() {
        return pausedTime;
    }

    /**
     * Adds a time of last pause to this job pause time counter. 
     */
    private void calcPausedTime() {
        this.pausedTime += System.currentTimeMillis() - this.getPauseStartDate();
    }


    /**
     * Returns the number of milliseconds this job effectively spent processing files, excluding any pause time.
     *
     * @return the number of milliseconds this job effectively spent processing files, excluding any pause time
     */
    public long getEffectiveJobTime() {
        // If job hasn't start yet, return 0
        if (getStartDate() == 0) {
            return 0;
        }
        
        return (getEndDate() == 0 ? System.currentTimeMillis() : getEndDate())-getStartDate()-getPausedTime();
    }

    
    /**
     * Interrupts this job, changes the job state to {@link State#INTERRUPTED} and notifies listeners.
     */	
    public void interrupt() {
        switch (getState()) {
            case INTERRUPTED:
            case FINISHED:
            return;
            case PAUSED:
            setPaused(false);
                break;
        }
        // Set state before calling stop() so that state is INTERRUPTED when jobStopped() is called
        // (some FileJob rely on that)
        setState(State.INTERRUPTED);

        stop();
    }


    /**
     * Release reference to thread and store job's end date.
     */
    private void stop() {
        // Return if job has already been stopped
        if (jobThread == null) {
            return;
        }

//        // Start by calling interrupt to have the thread return from any blocking I/O occurring in an interruptible
//        // channel or selector.
//        jobThread.interrupt();

        jobThread = null;
        endDate = System.currentTimeMillis();

        // Notify that the job has been stopped
        jobStopped();
    }

	
    /**
     * Sets or unsets this job in paused mode.
     * @param paused true to set in pause mode
     */
    public void setPaused(boolean paused) {
        // Lock the pause lock while updating paused status
        synchronized (pauseLock) {
            // Resume job if it was paused
            if (!paused && getState() == State.PAUSED) {
                // Calculate pause time
                calcPausedTime();                
                // Call the jobResumed method to notify of the new job's state
                jobResumed();

                // Wake up the job's thread that is potentially waiting for pause to be over 
                pauseLock.notify();

                // Switch to RUNNING state and notify listeners
                setState(State.RUNNING);
            }
            // Pause job if it not paused already
            else if(paused && getState() != State.PAUSED && getState() != State.INTERRUPTED && getState() != State.FINISHED) {
                // Memorize pause time in order to calculate pause time when the job is resumed
                setPauseStartDate();
                // Call the jobPaused method to notify of the new job's state
                jobPaused();

                // Switch to PAUSED state and notify listeners
                setState(State.PAUSED);
            }
        }
    }


    /**
     * Changes the current file. This method should be called by subclasses whenever the job
     * starts processing a new file other than a top-level file, i.e. one that was passed
     * as an argument to {@link #processFile(AbstractFile, Object) processFile()}.
     * ({#nextFile(AbstractFile) nextFile()} is automatically called for files in base folder).
     * @param file file to process
     */
    protected void nextFile(AbstractFile file) {
        this.setCurrentFile(file);

//        // Notify ProgressDialog (if any) that a new file is being processed
//        if(progressDialog!=null)
//            progressDialog.notifyCurrentFileChanged();
        
        // Lock the pause lock
        synchronized(pauseLock) {
            // Loop while job is paused, there shouldn't normally be more than one loop
            while (getState() == State.PAUSED) {
                try {
                    // Wait for a call to notify()
                    pauseLock.wait();
                } catch(InterruptedException e) {
                    // No more problem, loop one more time
                }
            }
        }
//        if(this.currentFile!=null)
//            this.nbFilesProcessed++;
    }


//    protected void fileDiscovered(AbstractFile file) {
//        this.nbFilesDiscovered++;
//    }
//
//    protected void filesDiscovered(AbstractFile files[]) {
//        this.nbFilesDiscovered += files.length;
//    }
//
//    protected int getNbFilesDiscovered() {
//        return this.nbFilesDiscovered;
//    }
//
//    protected int getNbFilesProcessed() {
//        return this.nbFilesProcessed;
//    }
//

    /**
     * Returns the name of the file currently being processed surrounded by simple quotes (e.g. 'test.zip'), or an empty
     * string if no file is currently being processed.
     *
     * @return the name of the file currently being processed surrounded by simple quotes, or an empty string if no file
     * is currently being processed
     */
    String getCurrentFilename() {
        return currentFilename;
    }


    /**
     * This method is called when this job starts, before the first call to {@link #processFile(AbstractFile,Object)} is made.
     * This method implementation does nothing but it can be overriden by subclasses to perform some first-time initializations.
     */
    protected void jobStarted() {
        LOGGER.debug("called");
    }
	

    /**
     * This method is called when this job has completed normal execution : all files have been processed without any interruption
     * (without any call to {@link #interrupt()}).
     *
     * <p>The call happens after the last call to {@link #processFile(AbstractFile,Object)} is made.
     * This method implementation does nothing but it can be overridden by subclasses to properly complete the job.
	 
     * <p>Note that this method will NOT be called if a call to {@link #interrupt()} was made before all files were processed.
     */
    protected void jobCompleted() {
        LOGGER.debug("called");

        // Send a system notification if a notifier is available and enabled
        if (AbstractNotifier.isAvailable() && AbstractNotifier.getNotifier().isEnabled()) {
            AbstractNotifier.getNotifier().displayBackgroundNotification(NotificationType.JOB_COMPLETED,
                    getProgressDialog() == null ? "" : getProgressDialog().getTitle(),
                    Translator.get("progress_dialog.job_finished"));
    }
    }


    /**
     * This method is called when this job has been paused, either by the user, or by the job when asking for user input.
     * 
     * <p>This method implementation does nothing but it can be overridden by subclasses to do whatever is needed
     * when the job has been paused.
     */
    protected void jobPaused() {
        LOGGER.debug("called");
    }


    /**
     * This method is called when this job has been resumed after being paused.
     *
     * <p>This method implementation does nothing but it can be overridden by subclasses to do whatever is needed
     * when the job has returned from pause.
     */
    protected void jobResumed() {
        LOGGER.debug("called");
    }


    /**
     * This method is called when this job has been stopped. The call happens after all calls to {@link #processFile(AbstractFile,Object)} and
     * {@link #jobCompleted()}.
     * This method implementation does nothing but it can be overridden by subclasses to properly terminate the job.
     * This is where you want to close any opened connections.
     *
     * <p>Note that unlike {@link #jobCompleted()} this method is always called, whether the job has been completed (all
     * files were processed) or has been interrupted in the middle.
     */
    protected void jobStopped() {
        LOGGER.debug("called");
    }
	
	
    /**
     * Displays an error dialog with the specified title and message,
     * offers to skip the file, retry or cancel and waits for user choice.
     * The job is stopped if 'cancel' or 'close' was chosen, and the result 
     * is returned.
     *
     * @param title error dialog title
     * @param message error dialog message
     */
    protected int showErrorDialog(String title, String message) {
        String actionTexts[] = new String[]{SKIP_TEXT, SKIP_ALL_TEXT, RETRY_TEXT, CANCEL_TEXT};
        int actionValues[] = new int[]{SKIP_ACTION, SKIP_ALL_ACTION, RETRY_ACTION, CANCEL_ACTION};

        return showErrorDialog(title, message, actionTexts, actionValues);
    }


	
    /**
     * Displays an error dialog with the specified title and message and returns the selection action's value.
     *
     * @param title error dialog title
     * @param message error dialog message
     * @param actionTexts actions text to display
     * @param actionValues actions return values
     */
    protected int showErrorDialog(String title, String message, String actionTexts[], int actionValues[]) {
        // Return SKIP_ACTION if 'skip all' has previously been selected and 'skip' is in the list of actions.
        if (autoSkipErrors) {
            for (int actionValue : actionValues)
                if (actionValue == SKIP_ACTION) {
                    return SKIP_ACTION;
                }
        }

        // Send a system notification if a notifier is available and enabled
        if (AbstractNotifier.isAvailable() && AbstractNotifier.getNotifier().isEnabled()) {
            AbstractNotifier.getNotifier().displayBackgroundNotification(NotificationType.JOB_ERROR, title, message);
        }

        QuestionDialog dialog;
        if (getProgressDialog() == null) {
            dialog = new QuestionDialog(getMainFrame(),
                                        title,
                                        message,
                                        getMainFrame(),
                                        actionTexts,
                                        actionValues,
                                        0);
        } else {
            dialog = new QuestionDialog(getProgressDialog(), 
                                        title,
                                        message,
                                        getMainFrame(),
                                        actionTexts,
                                        actionValues,
                                        0);
        }
        // Cancel or close dialog stops this job
        int userChoice = waitForUserResponse(dialog);
        if (userChoice == -1 || userChoice == CANCEL_ACTION) {
            interrupt();
        // Keep 'skip all' choice for further error and return SKIP_ACTION
        } else if (userChoice == SKIP_ALL_ACTION) {
            autoSkipErrors = true;
            return SKIP_ACTION;
        }

        return userChoice;
    }


    String enterRootPasswordDialog() {
        return new PasswordDialog(Translator.get("enter_root_password")).getPassword();
    }




    /**
     * Waits for the user's answer to the given question dialog, putting this
     * job in pause mode while waiting for the user.
     *
     * @param dialog object
     */
    int waitForUserResponse(DialogResult dialog) {
        Object userInput = waitForUserResponseObject(dialog);
        return (Integer) userInput;
    }
    
    Object waitForUserResponseObject(DialogResult dialog) {
        // Put this job in pause mode while waiting for user response
        setPaused(true);
        
        UserInputHelper jobUserInput = new UserInputHelper(this, dialog);
        Object userInput = jobUserInput.getUserInput();
        
        // Back to work
        setPaused(false);
        return userInput;
    }
    
	
	
    /**
     * Check and if needed, refreshes both file tables's current folders, based on the job's refresh policy.
     */
    private void refreshTables() {
    	FolderPanel activePanel = getMainFrame().getActivePanel();
    	FolderPanel inactivePanel = getMainFrame().getInactivePanel();

        if (hasFolderChanged(inactivePanel.getCurrentFolder())) {
        	inactivePanel.tryRefreshCurrentFolder();
        }

        if (hasFolderChanged(activePanel.getCurrentFolder())) {
            // Select file specified by selectFileWhenFinished (if any) only if the file exists in the active table's folder
            if (fileToSelect != null && activePanel.getCurrentFolder().equalsCanonical(fileToSelect.getParent()) && fileToSelect.exists()) {
            	activePanel.tryRefreshCurrentFolder(fileToSelect);
            } else {
            	activePanel.tryRefreshCurrentFolder();
        }
        }

        // Repaint the status bar as marked files have changed
        mainFrame.getStatusBar().updateSelectedFilesInfo();

        // Resume current folders auto-refresh
        getMainFrame().getLeftPanel().getFolderChangeMonitor().setPaused(false);
        getMainFrame().getRightPanel().getFolderChangeMonitor().setPaused(false);
    }
	

    /**
     * Returns this job's percentage of completion, as a float comprised between 0 and 1.
     *
     * @return this job's percentage of completion, as a float comprised between 0 and 1
     */
    public float getTotalPercentDone() {
        return getCurrentFileIndex()/(float)getNbFiles();
    }


    /**
     * Returns the index of the file currently being processed, {@link #getNbFiles()} if all files have been processed.
     *
     * @return the index of the file currently being processed, {@link #getNbFiles()} if all files have been processed
     */
    public int getCurrentFileIndex() {
        return currentFileIndex==-1?0:currentFileIndex;
    }
    
    /**
     * Returns the file currently being processed.
     * @return the file currently being processed.
     */
    public AbstractFile getCurrentFile() {
    	return currentFile;
    }
    
    /**
     * Sets the file currently being processed.
     * @param file the file currently being processed.
     */
    private void setCurrentFile(AbstractFile file) {
        this.currentFile = file;
        // Update current file information returned by getCurrentFilename()
        this.currentFilename = "'" + file.getName() + "'";
    }

    /**
     * Returns the number of file that this job contains.
     *
     * @return the number of file that this job contains
     */
    public int getNbFiles() {
        return nbFiles;
    }
    
    /**
     * Sets the number of files that this job contains.
     * 
     * @param nbFiles the number of files that this job contains.
     */
    protected void setNbFiles(int nbFiles) {
    	this.nbFiles = nbFiles;
    }

    public void setFiles(FileSet files) {
        this.files = files;
        this.nbFiles = files.size();
        this.baseSourceFolder = files.getBaseFolder();

        // create CachedFile instances around the source files in order to cache the return value of frequently accessed
        // methods. This eliminates some I/O, at the (small) cost of a bit more CPU and memory. Recursion is enabled
        // so that children and parents of the files are also cached.
        // Note: When cached methods are called, they no longer reflect changes in the underlying files. In particular,
        // changes of size or date could potentially not be reflected when files are being processed but this should
        // not really present a risk.
        for (int i = 0; i < nbFiles; i++) {
            AbstractFile tempFile = files.elementAt(i);
            files.setElementAt((tempFile instanceof CachedFile)?tempFile:new CachedFile(tempFile, true), i);
        }

        if (this.baseSourceFolder != null) {
            this.baseSourceFolder = (getBaseSourceFolder() instanceof CachedFile)?getBaseSourceFolder():new CachedFile(getBaseSourceFolder(), true);
        }
    }

    /**
     * Returns a String describing what the job is currently doing. This default implementation returns
     * <i>Processing CURRENT_FILE</i> where CURRENT_FILE is the name of the file currently being processed.
     * This method should be overridden to provide a more accurate description.
     *
     * @return a String describing what the job is currently doing
     */
    public String getStatusString() {
        return Translator.get("progress_dialog.processing_file", getCurrentFilename());
    }

    /**
     * Returns information about the job progress.
     * @return the job progress
     */
	public JobProgress getJobProgress() {
		return jobProgress;		
	}

    /**
     * Returns the base source folder.
     * @return the baseSourceFolder
     */
    AbstractFile getBaseSourceFolder() {
        return baseSourceFolder;
    }
	
	
    /////////////////////////////
    // Runnable implementation //
    /////////////////////////////

    /**
     * This method is public as a side-effect of this class implementing <code>Runnable</code>.
     */
    public final void run() {
        FileTable activeTable = getMainFrame().getActiveTable();

        // Notify that this job has started
        jobStarted();

//this.nbFilesDiscovered += nbFiles;

        // Loop on all source files, checking that job has not been interrupted
        for (currentFileIndex=0; currentFileIndex < nbFiles; currentFileIndex++) {
            AbstractFile currentFile = files.elementAt(currentFileIndex);

            // Change current file and advance file index
            nextFile(currentFile);

            // Process current file
            boolean success = processFile(currentFile, null);

            // Stop if job was interrupted
            if (getState() == State.INTERRUPTED)
                break;

            // Unmark file in active table if 'auto unmark' is enabled
            // and file was processed successfully
            if (autoUnmark && success) {
                // Do not repaint rows individually as it would be too expensive
                activeTable.setFileMarked(currentFile, false, false);
            }

            // If last file was reached without any user interruption, all files have been processed with or
            // without errors, switch to FINISHED state and notify listeners
            if (currentFileIndex >= nbFiles-1 && getState() != FileJob.State.INTERRUPTED) {
                currentFileIndex++;
                stop();
                jobCompleted();
                setState(State.FINISHED);
            }
        }

        // Refresh tables's current folders, based on the job's refresh policy.
        refreshTables();
    }


    //////////////////////
    // Abstract methods //
    //////////////////////

    /**
     * Returns <code>true</code> if the given folder has or may have been modified by this job.
     * This method is called after this job has finished processing files, to determine if the current MainFrame's
     * file tables need to be refreshed to reveal the modified contents.
     *
     * @param folder the folder to test 
     * @return true if the given folder has or may have been modified by this job
     */
    protected abstract boolean hasFolderChanged(AbstractFile folder);
	
	
    /**
     * Automatically called by {@link #run()} for each file that needs to be processed.
     *
     * @param file the file or folder to process
     * @param recurseParams array of parameters which can be used when calling this method recursively, contains <code>null</code> when called by {@link #run()}
     *
     * @return <code>true</code> if the operation was successful
     */
    protected abstract boolean processFile(AbstractFile file, Object recurseParams);


}
