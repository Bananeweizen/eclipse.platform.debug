package org.eclipse.debug.internal.ui.launchConfigurations;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * The dialog used to edit and launch launch configurations.
 */
public class LaunchConfigurationDialog extends TitleAreaDialog 
										implements ISelectionChangedListener, 
													ILaunchConfigurationListener, 
													ILaunchConfigurationDialog {
	
	/**
	 * The tree of launch configurations
	 */
	private TreeViewer fConfigTree;
	
	private Object fContext;
	
	private ILaunchConfiguration fFirstConfig;
	
	private Object fSelectedTreeObject;
	
	/**
	 * The starting mode, as specified by the caller
	 */
	private String fMode;
	
	/**
	 * The 'new' button to create a new configuration
	 */
	private Button fNewButton;
	
	/**
	 * The 'delete' button to delete selected configurations
	 */
	private Button fDeleteButton;
	
	/**
	 * The 'copy' button to create a copy of the selected config
	 */
	private Button fCopyButton;
	
	/**
	 * The 'save & launch' button
	 */
	private Button fSaveAndLaunchButton;
	
	/**
	 * The 'save' button
	 */
	private Button fSaveButton;	
	
	/**
	 * The 'launch' button
	 */
	private Button fLaunchButton;
	
	/**
	 * The 'cancel' button
	 */
	private Button fCancelButton;
	
	/**
	 * The text widget displaying the name of the
	 * launch configuration under edit
	 */
	private Text fNameText;
	
	private String fLastSavedName = null;
	
	/**
	 * The tab folder
	 */
	private TabFolder fTabFolder;
	
	/**
	 * The current (working copy) launch configuration
	 * being displayed/edited or <code>null</code> if
	 * none
	 */
	private ILaunchConfigurationWorkingCopy fWorkingCopy;
	
	private boolean fWorkingCopyVerifyState = false;
	
	/**
	 * The current tab extensions being displayed
	 */
	private ILaunchConfigurationTab[] fTabs;
	
	private ProgressMonitorPart fProgressMonitorPart;
	private Cursor waitCursor;
	private Cursor arrowCursor;
	private MessageDialog fWindowClosingDialog;
	
	private SelectionAdapter fCancelListener = new SelectionAdapter() {
		public void widgetSelected(SelectionEvent evt) {
			cancelPressed();
		}
	};
	
	/**
	 * Indicates whether callbacks on 'launchConfigurationChanged' should be treated
	 * as user changes.
	 */
	private boolean fChangesAreUserChanges = false;
	
	/**
	 * Indicates whether the current working copy has been dirtied by the user.
	 * This is the same as the more general notion of 'isDirty' on ILaunchConfigurationWorkingCopy,
	 * except that initializing defaults does not count as user dirty.
	 */
	private boolean fWorkingCopyUserDirty = false;
	
	/**
	 * Indicates if selection changes in the tree should be ignored
	 */
	private boolean fIgnoreSelectionChanges = false;
	
	/**
	 * The number of 'long-running' operations currently taking place in this dialog
	 */	
	private long fActiveRunningOperations = 0;
	
	/**
	 * Id for 'Save & Launch' button.
	 */
	protected static final int ID_SAVE_AND_LAUNCH_BUTTON = IDialogConstants.CLIENT_ID + 1;
		
	/**
	 * Id for 'Launch' button.
	 */
	protected static final int ID_LAUNCH_BUTTON = IDialogConstants.CLIENT_ID + 2;
	
	/**
	 * Constrant String used as key for setting and retrieving current Control with focus
	 */
	private static final String FOCUS_CONTROL = "focusControl";//$NON-NLS-1$

	/**
	 * The height in pixels of this dialog's progress indicator
	 */
	private static int PROGRESS_INDICATOR_HEIGHT = 18;

	/**
	 * Empty array
	 */
	protected static final Object[] EMPTY_ARRAY = new Object[0];	
	
	protected static final String DEFAULT_NEW_CONFIG_NAME = "New_configuration";
	
	/**
	 * Status area messages
	 */
	protected static final String LAUNCH_STATUS_OK_MESSAGE = "Ready to launch";
	protected static final String LAUNCH_STATUS_STARTING_FROM_SCRATCH_MESSAGE 
										= "Select a configuration to launch or a config type to create a new configuration";
	
	/**
	 * Constructs a new launch configuration dialog on the given
	 * parent shell.
	 * 
	 * @param shell the parent shell
	 * @param selection the selection used to initialize this dialog, typically the 
	 *  current workbench selection
	 * @param mode one of <code>ILaunchManager.RUN_MODE</code> or 
	 *  <code>ILaunchManager.DEBUG_MODE</code>
	 */
	public LaunchConfigurationDialog(Shell shell, IStructuredSelection selection, String mode) {
		super(shell);
		setContext(resolveContext(selection));
		setMode(mode);
	}
	
	/**
	 * Returns the Object to be used as context for this dialog, derived from the specified selection.
	 * If the specified selection has as its first element an IFile whose extension matches
	 * <code>ILaunchConfiguration.LAUNCH_CONFIGURATION_FILE_EXTENSION</code>, then return
	 * the launch configuration declared in the IFile.  Otherwise, return the first element 
	 * in the specified selection.
	 */
	protected Object resolveContext(IStructuredSelection selection) {
		
		// Empty selection means no context
		if ((selection == null) || (selection.size() < 1)) {
			return null;
		} 

		// If first element is a launch config file, create a launch configuration from it
		// and make this the context, otherwise just return the first element
		Object firstSelected = selection.getFirstElement();
		if (firstSelected instanceof IFile) {
			IFile file = (IFile) firstSelected;
			if (file.getFileExtension().equals(ILaunchConfiguration.LAUNCH_CONFIGURATION_FILE_EXTENSION)) {
				return getLaunchManager().getLaunchConfiguration(file);
			}
		}
		return firstSelected;
	}
	
	/**
	 * A launch configuration dialog overrides this method
	 * to create a custom set of buttons in the button bar.
	 * This dialog has 'Save & Launch', 'Launch', and 'Cancel'
	 * buttons.
	 * 
	 * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(Composite)
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		setSaveAndLaunchButton(createButton(parent, ID_SAVE_AND_LAUNCH_BUTTON, "Sa&ve and Launch", false));
		setLaunchButton(createButton(parent, ID_LAUNCH_BUTTON, "&Launch", true));
		setCancelButton(createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false));
	}	

	/**
	 * Handle the 'save and launch' & 'launch' buttons here, all others are handled
	 * in <code>Dialog</code>
	 * 
	 * @see Dialog#buttonPressed(int)
	 */
	protected void buttonPressed(int buttonId) {
		if (buttonId == ID_SAVE_AND_LAUNCH_BUTTON) {
			handleSaveAndLaunchPressed();
		} else if (buttonId == ID_LAUNCH_BUTTON) {
			handleLaunchPressed();
		} else {
			super.buttonPressed(buttonId);
		}
	}

	/**
	 * @see Dialog#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		Control contents = super.createContents(parent);
		getLaunchManager().addLaunchConfigurationListener(this);
		displayFirstConfig();
		return contents;
	}
	
	/**
	 * Display the first configuration in this dialog.
	 */
	protected void displayFirstConfig() {
		IStructuredSelection selection = StructuredSelection.EMPTY;
		if (fFirstConfig instanceof ILaunchConfigurationWorkingCopy) {
			try {
				ILaunchConfigurationType firstConfigType = fFirstConfig.getType();
				selection = new StructuredSelection(firstConfigType);
				setIgnoreSelectionChanges(true);
				getTreeViewer().setSelection(selection);			
				setIgnoreSelectionChanges(false);
				setLaunchConfiguration(fFirstConfig);
			} catch (CoreException ce) {
			}
		} else if (fFirstConfig instanceof ILaunchConfiguration) {
			selection = new StructuredSelection(fFirstConfig);			
			getTreeViewer().setSelection(selection);			
		} else {
			getTreeViewer().setSelection(selection);
		}
	}
	
	/**
	 * Determine the first configuration for this dialog.  If this configuration verifies
	 * and the 'single-click launching' preference is turned on, launch the configuration
	 * WITHOUT realizing the dialog.  Otherwise, call super.open(), which will realize the
	 * dialog and display the first configuration.
	 * 
	 * @see Window#open()
	 */
	public int open() {
		fFirstConfig = determineConfigFromContext();
		if (fFirstConfig != null) {
			if (getPreferenceStore().getBoolean(IDebugUIConstants.PREF_SINGLE_CLICK_LAUNCHING)) {				
				try {
					fFirstConfig.verify(getMode());
					fFirstConfig.launch(getMode());
					return ILaunchConfigurationDialog.SINGLE_CLICK_LAUNCHED;
				} catch (CoreException ce) {				
				}
			}
		}
		return super.open();
	}
	
	/**
	 * @see Window#close()
	 */
	public boolean close() {
		getLaunchManager().removeLaunchConfigurationListener(this);
		return super.close();
	}
	
	/**
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		GridData gd;
		Composite dialogComp = (Composite)super.createDialogArea(parent);
		Composite topComp = new Composite(dialogComp, SWT.NONE);
		GridLayout topLayout = new GridLayout();
		topLayout.numColumns = 2;
		topLayout.marginHeight = 5;
		topLayout.marginWidth = 0;
		topComp.setLayout(topLayout);

		// Set the things that TitleAreaDialog takes care of
		setTitle("Create, manage, and run launch configurations");
		setMessage("Ready to launch");
		setModeLabelState();

		// Build the launch configuration selection area
		// and put it into the composite.
		Composite launchConfigSelectionArea = createLaunchConfigurationSelectionArea(topComp);
		gd = new GridData(GridData.FILL_VERTICAL);
		launchConfigSelectionArea.setLayoutData(gd);
	
		// Build the launch configuration edit area
		// and put it into the composite.
		Composite launchConfigEditArea = createLaunchConfigurationEditArea(topComp);
		gd = new GridData(GridData.FILL_BOTH);
		launchConfigEditArea.setLayoutData(gd);
			
		// Build the separator line
		Label separator = new Label(topComp, SWT.HORIZONTAL | SWT.SEPARATOR);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		separator.setLayoutData(gd);
		
		return dialogComp;
	}
	
	/**
	 * Determine and return an <code>ILaunchConfiguration</code> from the current context.
	 * If the context is itself an ILaunchConfiguration, this is returned.  Otherwise,
	 * an <code>ILaunchConfigurationWorkingCopy</code> is created and initialized from 
	 * the context if possible.
	 */
	protected ILaunchConfiguration determineConfigFromContext() {
		Object workbenchSelection = getContext();
		if (workbenchSelection == null) {
			return null;
		}
		
		if (workbenchSelection instanceof ILaunchConfiguration) {
			return (ILaunchConfiguration) workbenchSelection;
		} else {
			return createConfigFromContext();
		}
	}
	
	/**
	 * If an actual <code>ILaunchConfiguration</code> was selected in
	 * the workbench, select it in the tree.  	
	 */
	/*
	protected void initializeFirstConfigForConfiguration() {
		IStructuredSelection selection = new StructuredSelection(getContext());
		setTreeViewerSelection(selection);
	}
	*/
	
	/**
	 * Something other than an <code>ILaunchConfiguration</code> was selected in
	 * the workbench, so try to determine an <code>ILaunchConfigurationType</code>
	 * from the selection, then create a new working copy of that type, initialize
	 * its default values, set this new launch configuration so that the edit area
	 * tabs get populated, and finally make sure the config type is selected in the 
	 * configuration tree.
	 */
	protected ILaunchConfigurationWorkingCopy createConfigFromContext() {
		ILaunchConfigurationType configType = determineConfigTypeFromContext();
		if (configType == null) {
			return null;
		}
		
		ILaunchConfigurationWorkingCopy workingCopy = null;
		try {
			setChangesAreUserChanges(false);
			workingCopy = configType.newInstance(null, DEFAULT_NEW_CONFIG_NAME);
			workingCopy.initializeDefaults(getContext());
		} catch (CoreException ce) {
		}
		
		return workingCopy;
		
		/*
		setLaunchConfiguration(workingCopy);
		IStructuredSelection selection = new StructuredSelection(configType);
		setTreeViewerSelection(selection);
		*/
	}
	
	/**
	 * Attempt to determine the launch config type most closely associated
	 * with the current workbench selection.
	 */
	protected ILaunchConfigurationType determineConfigTypeFromContext() {		
		IResource resource = null;
		Object workbenchSelection = getContext();
		if (workbenchSelection instanceof IResource) {
			resource = (IResource)workbenchSelection;
		} else if (workbenchSelection instanceof IAdaptable) {
			resource = (IResource) ((IAdaptable)workbenchSelection).getAdapter(IResource.class);
		}
		ILaunchConfigurationType type = null;
		if (resource != null) {
			type = getLaunchManager().getDefaultLaunchConfigurationType(resource, false);
		}
		return type;		
	}
	
	/**
	 * Set the title area image based on the mode this dialog was initialized with
	 */
	protected void setModeLabelState() {
		Image image;
		if (getMode().equals(ILaunchManager.DEBUG_MODE)) {
			image = DebugUITools.getImage(IDebugUIConstants.IMG_WIZBAN_DEBUG);
		} else {
			image = DebugUITools.getImage(IDebugUIConstants.IMG_WIZBAN_RUN);
		}
		setTitleImage(image);
	}
	
	/**
	 * Convenience method to set the selection on the configuration tree.
	 */
	protected void setTreeViewerSelection(ISelection selection) {
		getTreeViewer().setSelection(selection);
	}
	
	private void setLastSavedName(String lastSavedName) {
		this.fLastSavedName = lastSavedName;
	}

	private String getLastSavedName() {
		return fLastSavedName;
	}
	
	/**
	 * If verifying fails, we catch a <code>CoreException</code>, in which
	 * case we extract the error message and set it in the status area.
	 * Otherwise, we set the "ready to launch" message in the status area.
	 * 
	 * @see ILaunchConfigurationDialog#refreshStatus()
	 */
	public void refreshStatus() {
		
		// If there is no working copy, then the user is starting from scratch
		ILaunchConfigurationWorkingCopy workingCopy = getWorkingCopy();
		if (workingCopy == null) {
			setWorkingCopyVerifyState(false);
			setErrorMessage(null);
			setEnableStateEditButtons();
			return;
		}
		
		// Verify the working copy.  Any CoreExceptions indicate a problem with
		// the working copy, so update the status area and internal state accordingly
		try {
			verify(getWorkingCopy());
		} catch (CoreException ce) {
			setWorkingCopyVerifyState(false);
			String message = ce.getStatus().getMessage();
			setErrorMessage(message);
			setEnableStateEditButtons();
			return;
		}	
		
		// Otherwise the verify was successful, update status area and internal state 	
		setWorkingCopyVerifyState(true);
		setErrorMessage(null);
		setEnableStateEditButtons();
	}
	
	/**
	 * Verify the working copy
	 */
	protected void verify(ILaunchConfiguration config) throws CoreException {
		config.verify(getMode());
		verifyStandardAttributes();	
	}
	
	/**
	 * Verify the attributes common to all launch configuration.
	 * To be consistent with <code>ILaunchConfiguration.verify</code>,
	 * indicate failure by throwing a <code>CoreException</code>.
	 */
	protected void verifyStandardAttributes() throws CoreException {
		verifyName();
	}
	
	/**
	 * Verify that there are no name collisions.
	 */
	protected void verifyName() throws CoreException {
		String currentName = getNameTextWidget().getText();

		// If there is no name, complain
		if (currentName.trim().length() < 1) {
			throw new CoreException(new Status(IStatus.ERROR,
												 DebugUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(),
												 0,
												 "Name required for launch configuration.",
												 null));			
		}

		// If the name hasn't changed from the last saved name, do nothing
		if (currentName.equals(getLastSavedName())) {
			return;
		}				
		
		// Otherwise, if there's already a config with the same name, complain
		if (getLaunchManager().isExistingLaunchConfigurationName(currentName)) {
			throw new CoreException(new Status(IStatus.ERROR,
												 DebugUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(),
												 0,
												 "Launch configuration already exists with this name.",
												 null));						
		}						
	}
	
	protected void updateConfigFromName() {
		if (getWorkingCopy() != null) {
			getWorkingCopy().rename(getNameTextWidget().getText());
			refreshStatus();
		}
	}
	
	protected Display getDisplay() {
		return getShell().getDisplay();
	}
		
	/**
	 * Creates the launch configuration selection area of the dialog.
	 * This area displays a tree of launch configrations that the user
	 * may select, and allows users to create new configurations, and
	 * delete and copy existing configurations.
	 * 
	 * @return the composite used for launch configuration selection
	 */ 
	protected Composite createLaunchConfigurationSelectionArea(Composite parent) {
		Composite c = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginHeight = 0;
		layout.marginWidth = 5;
		c.setLayout(layout);
		
		GridData gd;
		
		TreeViewer tree = new TreeViewer(c);
		gd = new GridData(GridData.FILL_VERTICAL);
		gd.horizontalSpan = 3;
		gd.widthHint = 200;
		gd.heightHint = 375;
		tree.getControl().setLayoutData(gd);
		tree.setContentProvider(new LaunchConfigurationContentProvider());
		tree.setLabelProvider(DebugUITools.newDebugModelPresentation());
		setTreeViewer(tree);
		tree.addSelectionChangedListener(this);
		tree.setInput(ResourcesPlugin.getWorkspace().getRoot());		
		
		Button newButton = new Button(c, SWT.PUSH | SWT.CENTER);
		newButton.setText("Ne&w");
		gd = new GridData(GridData.BEGINNING);
		gd.horizontalSpan = 1;
		newButton.setLayoutData(gd);
		setNewButton(newButton);
		
		newButton.addSelectionListener(
			new SelectionAdapter() { 
				public void widgetSelected(SelectionEvent event) {
					handleNewPressed();
				}
			}
		);				
		
		Button deleteButton = new Button(c, SWT.PUSH | SWT.CENTER);
		deleteButton.setText("Dele&te");
		gd = new GridData(GridData.CENTER);
		gd.horizontalSpan = 1;
		deleteButton.setLayoutData(gd);
		setDeleteButton(deleteButton);
		
		deleteButton.addSelectionListener(
			new SelectionAdapter() { 
				public void widgetSelected(SelectionEvent event) {
					handleDeletePressed();
				}
			}
		);			
		
		Button copyButton = new Button(c, SWT.PUSH | SWT.CENTER);
		copyButton.setText("Cop&y");
		gd = new GridData(GridData.END);
		gd.horizontalSpan = 1;
		copyButton.setLayoutData(gd);
		setCopyButton(copyButton);
		
		copyButton.addSelectionListener(
			new SelectionAdapter() { 
				public void widgetSelected(SelectionEvent event) {
					handleCopyPressed();
				}
			}
		);			
		
		return c;
	}	
	
	/**
	 * Creates the launch configuration edit area of the dialog.
	 * This area displays the name of the launch configuration
	 * currently being edited, as well as a tab folder of tabs
	 * that are applicable to the launch configuration.
	 * 
	 * @return the composite used for launch configuration editing
	 */ 
	protected Composite createLaunchConfigurationEditArea(Composite parent) {
		Composite c = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		layout.marginWidth = 5;
		c.setLayout(layout);
		
		GridData gd;
		
		Label nameLabel = new Label(c, SWT.HORIZONTAL | SWT.LEFT);
		nameLabel.setText("&Name:");
		gd = new GridData(GridData.BEGINNING);
		nameLabel.setLayoutData(gd);
		
		Text nameText = new Text(c, SWT.SINGLE | SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		nameText.setLayoutData(gd);
		setNameTextWidget(nameText);
		
		getNameTextWidget().addModifyListener(
			new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					updateConfigFromName();
				}
			}
		);		
		
		Label spacer = new Label(c, SWT.NONE);
		gd = new GridData();
		gd.horizontalSpan = 2;
		spacer.setLayoutData(gd);
		
		TabFolder tabFolder = new TabFolder(c, SWT.NONE);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		gd.heightHint = 375;
		gd.widthHint = 375;
		tabFolder.setLayoutData(gd);
		setTabFolder(tabFolder);
		
		Button saveButton = new Button(c, SWT.PUSH | SWT.CENTER);
		saveButton.setText("&Save");
		gd = new GridData(GridData.HORIZONTAL_ALIGN_END);
		gd.horizontalSpan = 2;
		saveButton.setLayoutData(gd);
		setSaveButton(saveButton);
		getSaveButton().addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent evt) {
				handleSavePressed();
			}
		});
		
		return c;
	}	
	
	/**
	 * @see Dialog#createButtonBar(Composite)
	 */
	protected Control createButtonBar(Composite parent) {
		Composite composite= new Composite(parent, SWT.NULL);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		GridLayout pmLayout = new GridLayout();
		pmLayout.numColumns = 2;
		setProgressMonitorPart(new ProgressMonitorPart(composite, pmLayout, PROGRESS_INDICATOR_HEIGHT));
		getProgressMonitorPart().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		getProgressMonitorPart().setVisible(false);

		return super.createButtonBar(composite);
	}
	
	/**
	 * Sets the title for the dialog and establishes the help context.
	 * 
	 * @see org.eclipse.jface.window.Window#configureShell(Shell);
	 */
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Launch Configurations");
		WorkbenchHelp.setHelp(
			shell,
			new Object[] { IDebugHelpContextIds.LAUNCH_CONFIGURATION_DIALOG });
	}
	
	/**
	 * Sets the tree viewer used to display launch configurations.
	 * 
	 * @param viewer the tree viewer used to display launch
	 *  configurations
	 */
	private void setTreeViewer(TreeViewer viewer) {
		fConfigTree = viewer;
	}
	
	/**
	 * Returns the tree viewer used to display launch configurations.
	 * 
	 * @param the tree viewer used to display launch configurations
	 */
	protected TreeViewer getTreeViewer() {
		return fConfigTree;
	}
	
	protected Object getTreeViewerFirstSelectedElement() {
		IStructuredSelection selection = (IStructuredSelection)getTreeViewer().getSelection();
		if (selection == null) {
			return null;
		}
		return selection.getFirstElement();
	}
		
	/**
	 * Content provider for launch configuration tree
	 */
	class LaunchConfigurationContentProvider implements ITreeContentProvider {
		
		/**
		 * @see ITreeContentProvider#getChildren(Object)
		 */
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof ILaunchConfiguration) {
				return EMPTY_ARRAY;
			} else if (parentElement instanceof ILaunchConfigurationType) {
				try {
					ILaunchConfigurationType type = (ILaunchConfigurationType)parentElement;
					// all configs in workspace of a specific type
					return getLaunchManager().getLaunchConfigurations(type);
				} catch (CoreException e) {
					DebugUIPlugin.errorDialog(getShell(), "Error", "An exception occurred while retrieving launch configurations.", e.getStatus());
				}
			} else {
				return getLaunchManager().getLaunchConfigurationTypes();
			}
			return EMPTY_ARRAY;
		}

		/**
		 * @see ITreeContentProvider#getParent(Object)
		 */
		public Object getParent(Object element) {
			if (element instanceof ILaunchConfiguration) {
				if (!((ILaunchConfiguration)element).exists()) {
					return null;
				}
				try {
					return ((ILaunchConfiguration)element).getType();
				} catch (CoreException e) {
					DebugUIPlugin.errorDialog(getShell(), "Error", "An exception occurred while retrieving launch configurations.", e.getStatus());
				}
			} else if (element instanceof ILaunchConfigurationType) {
				return ResourcesPlugin.getWorkspace().getRoot();
			}
			return null;
		}

		/**
		 * @see ITreeContentProvider#hasChildren(Object)
		 */
		public boolean hasChildren(Object element) {
			if (element instanceof ILaunchConfiguration) {
				return false;
			} else {
				return getChildren(element).length > 0;
			}
		}

		/**
		 * Return only the launch configuration types that support the current mode.
		 * @see IStructuredContentProvider#getElements(Object)
		 */
		public Object[] getElements(Object inputElement) {
			ILaunchConfigurationType[] allTypes = getLaunchManager().getLaunchConfigurationTypes();
			ArrayList list = new ArrayList(allTypes.length);
			String mode = getMode();
			for (int i = 0; i < allTypes.length; i++) {
				if (allTypes[i].supportsMode(mode)) {
					list.add(allTypes[i]);
				}
			}			
			return list.toArray();
		}

		/**
		 * @see IContentProvider#dispose()
		 */
		public void dispose() {
		}

		/**
		 * @see IContentProvider#inputChanged(Viewer, Object, Object)
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

	}
	
	/**
	 * Returns the launch manager.
	 * 
	 * @return the launch manager
	 */
	protected ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	/**
	 * Returns whether this dialog is currently open
	 */
	protected boolean isVisible() {
		return getTreeViewer() != null;
	}	
		
	/**
	 * Notification that selection has changed in the launch configuration tree.
	 * <p>
	 * If the currently displayed configuration is not saved,
	 * prompt for saving before moving on to the new selection.
	 * </p>
	 * 
	 * @param event selection changed event
	 */
 	public void selectionChanged(SelectionChangedEvent event) {
 		
 		// Ignore selectionChange events that occur while saving
 		if (ignoreSelectionChanges()) {
 			return;
 		}
 		
 		// Get the selection		
 		IStructuredSelection selection = (IStructuredSelection)event.getSelection();
 		Object firstSelectedElement = selection.getFirstElement();		
 		boolean singleSelection = selection.size() == 1;
		boolean configSelected = firstSelectedElement instanceof ILaunchConfiguration;

 		// If selection is the same, don't bother
 		if (getSelectedTreeObject() == firstSelectedElement) {
 			return;
 		}
 		
		// Take care of any unsaved changes.  If the user aborts, reset selection
		// to whatever it was previously selected
 		if (!canReplaceWorkingCopy()) {
 			StructuredSelection prevSelection;
 			Object selectedTreeObject = getSelectedTreeObject();
			if (selectedTreeObject == null) {
				prevSelection = StructuredSelection.EMPTY;
			} else {
				prevSelection = new StructuredSelection(selectedTreeObject);
			}
 			setTreeViewerSelection(prevSelection);
 			return;
 		}			
 				
 		// Enable tree-related buttons
 		setEnableStateTreeButtons(configSelected);
 		 		 
		// If a config is selected, update the edit area for it, if a config type is
		// selected, behave as if user clicked 'New'
 		if (singleSelection && configSelected) {
 			ILaunchConfiguration config = (ILaunchConfiguration) firstSelectedElement; 			
 			setLastSavedName(config.getName());
 			setLaunchConfiguration(config);
 		} else if (singleSelection && firstSelectedElement instanceof ILaunchConfigurationType) {
			constructNewConfig();
 		} else {
 			// multi-selection
 			clearLaunchConfiguration();
 		}
 		
 		setSelectedTreeObject(firstSelectedElement);
 	}
 	
 	/**
 	 * Set the enabled state of the buttons that relate to the tree viewer.  Note that
 	 * the 'New' button is always enabled.
 	 */
 	protected void setEnableStateTreeButtons(boolean enable) {
		getCopyButton().setEnabled(enable);
		getDeleteButton().setEnabled(enable); 		
 	}
 	
 	/**
 	 * Set the enabled state of the buttons that appear on the edit side of the dialog
 	 * (the 'save' & 'launch' buttons).
 	 */
 	protected void setEnableStateEditButtons() {
		boolean verifies = getWorkingCopyVerifyState();
		boolean dirty = isWorkingCopyDirty();
		getSaveButton().setEnabled(verifies && dirty);
		getSaveAndLaunchButton().setEnabled(verifies && dirty);
		getLaunchButton().setEnabled(verifies);
 		
 	}
 
 	/**
 	 * Sets the 'save & launch' button.
 	 * 
 	 * @param button the 'save & launch' button.
 	 */	
 	private void setSaveAndLaunchButton(Button button) {
 		fSaveAndLaunchButton = button;
 	}
 	
 	/**
 	 * Returns the 'save & launch' button
 	 * 
 	 * @return the 'save & launch' button
 	 */
 	protected Button getSaveAndLaunchButton() {
 		return fSaveAndLaunchButton;
 	}
 	
 	/**
 	 * Sets the 'launch' button.
 	 * 
 	 * @param button the 'launch' button.
 	 */	
 	private void setLaunchButton(Button button) {
 		fLaunchButton = button;
 	} 	
 	
 	/**
 	 * Returns the 'launch' button
 	 * 
 	 * @return the 'launch' button
 	 */
 	protected Button getLaunchButton() {
 		return fLaunchButton;
 	} 	
 	
 	protected void setCancelButton(Button button) {
 		fCancelButton = button;
 	}
 	
 	protected Button getCancelButton() {
 		return fCancelButton;
 	}
 	
 	protected void setProgressMonitorPart(ProgressMonitorPart part) {
 		fProgressMonitorPart = part;
 	}
 	
 	protected ProgressMonitorPart getProgressMonitorPart() {
 		return fProgressMonitorPart;
 	}
 	
 	/**
 	 * Sets the 'new' button.
 	 * 
 	 * @param button the 'new' button.
 	 */	
 	private void setNewButton(Button button) {
 		fNewButton = button;
 	} 	
 	
  	/**
 	 * Returns the 'new' button
 	 * 
 	 * @return the 'new' button
 	 */
 	protected Button getNewButton() {
 		return fNewButton;
 	}
 	
 	/**
 	 * Sets the 'delete' button.
 	 * 
 	 * @param button the 'delete' button.
 	 */	
 	private void setDeleteButton(Button button) {
 		fDeleteButton = button;
 	} 	
 	
 	/**
 	 * Returns the 'delete' button
 	 * 
 	 * @return the 'delete' button
 	 */
 	protected Button getDeleteButton() {
 		return fDeleteButton;
 	}
 	 	
 	/**
 	 * Sets the 'copy' button.
 	 * 
 	 * @param button the 'copy' button.
 	 */	
 	private void setCopyButton(Button button) {
 		fCopyButton = button;
 	} 	
 	
 	/**
 	 * Returns the 'copy' button
 	 * 
 	 * @return the 'copy' button
 	 */
 	protected Button getCopyButton() {
 		return fCopyButton;
 	} 	
 	
 	/**
 	 * Sets the configuration to display/edit.
 	 * Updates the tab folder to contain the appropriate pages.
 	 * Sets all configuration-related state appropriately.
 	 * 
 	 * @param config the launch configuration to display/edit
 	 */
 	protected void setLaunchConfiguration(ILaunchConfiguration config) {
		try {
			setTabsForConfigType(config.getType());
			
			if (config.isWorkingCopy()) {
		 		setWorkingCopy((ILaunchConfigurationWorkingCopy)config);
			} else {
				setWorkingCopy(config.getWorkingCopy());
			}
	 		// update the name field
	 		getNameTextWidget().setText(config.getName());
	 		
	 		// Reset internal flags so user edits are recognized as making the
	 		// working copy 'user dirty'
	 		setChangesAreUserChanges(true);
	 		setWorkingCopyUserDirty(false);
	 		
	 		// update the tabs with the new working copy
	 		ILaunchConfigurationTab[] tabs = getTabs();
	 		for (int i = 0; i < tabs.length; i++) {
				tabs[i].setLaunchConfiguration(getWorkingCopy());
	 		}	 		
	 		refreshStatus();
		} catch (CoreException ce) {
 			DebugUIPlugin.errorDialog(getShell(), "Error", "Exception occurred setting launch configuration", ce.getStatus());
 			clearLaunchConfiguration();
 			return;					
		}
 	} 
 	
 	/**
 	 * Clears the configuration being shown/edited.  
 	 * Removes all tabs from the tab folder.  
 	 * Resets all configuration-related state.
 	 */
 	protected void clearLaunchConfiguration() {
 		setWorkingCopy(null);
 		setWorkingCopyVerifyState(false);
		setChangesAreUserChanges(false);
 		setWorkingCopyUserDirty(false);
 		setLastSavedName(null);
 		getNameTextWidget().setText(""); 
 		disposeExistingTabs();
 		refreshStatus();
 	}
 	
 	/**
 	 * Populate the tabs in the configuration edit area to be appropriate to the current
 	 * launch configuration type.
 	 */
 	protected void setTabsForConfigType(ILaunchConfigurationType configType) {		
		// dispose the current tabs
		disposeExistingTabs();

		// build the new tabs
 		LaunchConfigurationTabExtension[] exts = LaunchConfigurationPresentationManager.getDefault().getTabs(configType);
 		ILaunchConfigurationTab[] tabs = new ILaunchConfigurationTab[exts.length];
 		for (int i = 0; i < exts.length; i++) {
 			TabItem tab = new TabItem(getTabFolder(), SWT.NONE);
 			String name = exts[i].getName();
 			if (name == null) {
 				name = "unspecified";
 			}
 			tab.setText(name);
 			try {
	 			tabs[i] = (ILaunchConfigurationTab)exts[i].getConfigurationElement().createExecutableExtension("class");
 			} catch (CoreException ce) {
	 			DebugUIPlugin.errorDialog(getShell(), "Error", "Exception occurred creating launch configuration tabs",ce.getStatus());
 				return; 				
 			}
 			Control control = tabs[i].createTabControl(this, tab);
 			if (control != null) {
	 			tab.setControl(control);
 			}
 		}
 		setTabs(tabs);	
 	}
 	
 	protected void disposeExistingTabs() {
		TabItem[] oldTabs = getTabFolder().getItems();
		ILaunchConfigurationTab[] tabs = getTabs();
		for (int i = 0; i < oldTabs.length; i++) {
			oldTabs[i].dispose();
			tabs[i].dispose();
		} 		
		setTabs(null);
 	}
 	
 	/**
 	 * Sets the current launch configuration that is being
 	 * displayed/edited.
 	 */
 	protected void setWorkingCopy(ILaunchConfigurationWorkingCopy workingCopy) {
 		fWorkingCopy = workingCopy;
 	}
 	
 	/**
 	 * Returns the current launch configuration that is being
 	 * displayed/edited.
 	 * 
 	 * @return current configuration being displayed
 	 */
 	protected ILaunchConfigurationWorkingCopy getWorkingCopy() {
 		return fWorkingCopy;
 	}
 	
 	protected void setWorkingCopyVerifyState(boolean state) {
 		fWorkingCopyVerifyState = state;
 	}
 	
 	protected boolean isWorkingCopyDirty() {
 		ILaunchConfigurationWorkingCopy workingCopy = getWorkingCopy();
 		if (workingCopy == null) {
 			return false;
 		}
 		return workingCopy.isDirty();
 	}
 	
 	protected boolean getWorkingCopyVerifyState() {
 		return fWorkingCopyVerifyState;
 	}
 	
 	protected void setChangesAreUserChanges(boolean state) {
 		fChangesAreUserChanges = state;
 	}
 	
 	protected boolean areChangesUserChanges() {
 		return fChangesAreUserChanges;
 	}
 	
 	protected void setWorkingCopyUserDirty(boolean dirty) {
 		fWorkingCopyUserDirty = dirty;
 	}
 	
 	protected boolean isWorkingCopyUserDirty() {
 		return fWorkingCopyUserDirty;
 	}
 	
 	protected void setContext(Object context) {
 		fContext = context;
 	}
 	
 	protected Object getContext() {
 		return fContext;
 	}
 	
 	protected void setSelectedTreeObject(Object selection) {
 		fSelectedTreeObject = selection;
 	}
 	
 	protected Object getSelectedTreeObject() {
 		return fSelectedTreeObject;
 	}
 	
 	protected void setMode(String mode) {
 		fMode = mode;
 	}
 	
 	protected String getMode() {
 		return fMode;
 	}
 	
	/**
	 * Sets the text widget used to display the name
	 * of the configuration being displayed/edited
	 * 
	 * @param widget the text widget used to display the name
	 *  of the configuration being displayed/edited
	 */
	private void setNameTextWidget(Text widget) {
		fNameText = widget;
	}
	
	/**
	 * Returns the text widget used to display the name
	 * of the configuration being displayed/edited
	 * 
	 * @return the text widget used to display the name
	 *  of the configuration being displayed/edited
	 */
	protected Text getNameTextWidget() {
		return fNameText;
	} 
	
 	/**
 	 * Sets the 'save' button.
 	 * 
 	 * @param button the 'save' button.
 	 */	
 	private void setSaveButton(Button button) {
 		fSaveButton = button;
 	}
 	
 	/**
 	 * Returns the 'save' button
 	 * 
 	 * @return the 'save' button
 	 */
 	protected Button getSaveButton() {
 		return fSaveButton;
 	}	
 	
 	/**
 	 * Sets the tab folder
 	 * 
 	 * @param folder the tab folder
 	 */	
 	private void setTabFolder(TabFolder folder) {
 		fTabFolder = folder;
 	}
 	
 	/**
 	 * Returns the tab folder
 	 * 
 	 * @return the tab folder
 	 */
 	protected TabFolder getTabFolder() {
 		return fTabFolder;
 	}	 	
 	
 	/**
 	 * Sets the current tab extensions being displayed
 	 * 
 	 * @param tabs the current tab extensions being displayed
 	 */
 	private void setTabs(ILaunchConfigurationTab[] tabs) {
 		fTabs = tabs;
 	}
 	
 	/**
 	 * Returns the current tab extensions being displayed
 	 * 
 	 * @return the current tab extensions being displayed
 	 */
 	protected ILaunchConfigurationTab[] getTabs() {
 		return fTabs;
 	} 	
 	
	/**
	 * @see ILaunchConfigurationListener#launchConfigurationAdded(ILaunchConfiguration)
	 */
	public void launchConfigurationAdded(ILaunchConfiguration configuration) {
		getTreeViewer().refresh();		
	}

	/**
	 * @see ILaunchConfigurationListener#launchConfigurationChanged(ILaunchConfiguration)
	 */
	public void launchConfigurationChanged(ILaunchConfiguration configuration) {
		if (areChangesUserChanges()) {
			setWorkingCopyUserDirty(true);
		}
	}

	/**
	 * @see ILaunchConfigurationListener#launchConfigurationRemoved(ILaunchConfiguration)
	 */
	public void launchConfigurationRemoved(ILaunchConfiguration configuration) {
		getTreeViewer().remove(configuration);		
	}
	
	protected void setIgnoreSelectionChanges(boolean ignore) {
		fIgnoreSelectionChanges = ignore;
	}
	
	protected boolean ignoreSelectionChanges() {
		return fIgnoreSelectionChanges;
	}
	
	/**
	 * Return whether the current working copy can be replaced with a new working copy.
	 */
	protected boolean canReplaceWorkingCopy() {
		
		// If there is no working copy, there's no problem, return true
		ILaunchConfigurationWorkingCopy workingCopy = getWorkingCopy();
		if (workingCopy == null) {
			return true;
		}
		
		// If the working copy doesn't verify, show user dialog asking if they wish
		// to discard their changes.  Otherwise, if the working copy is dirty,
		// show a slightly different 'save changes' dialog.
		if (isWorkingCopyUserDirty() && !getWorkingCopyVerifyState()) {
			StringBuffer buffer = new StringBuffer("The configuration \"");
			buffer.append(getWorkingCopy().getName());
			buffer.append("\" CANNOT be saved.  Do you wish to discard changes?");
			return MessageDialog.openQuestion(getShell(), "Discard changes?", buffer.toString());
		} else {
			if (isWorkingCopyUserDirty()) {
				return showSaveChangesDialog();
			} else {
				return true;
			}
		}
	}
	
	/**
	 * Show the user a dialog with specified title, message and buttons.  
	 * Return true if the user hit 'CANCEL', false otherwise.
	 */
	protected boolean showSaveChangesDialog() {
		StringBuffer buffer = new StringBuffer("The configuration \"");
		buffer.append(getWorkingCopy().getName());
		buffer.append("\" has unsaved changes.  Do you wish to save them?");
		MessageDialog dialog = new MessageDialog(getShell(), 
												 "Save changes?",
												 null,
												 buffer.toString(),
												 MessageDialog.QUESTION,
												 new String[] {"Yes", "No", "Cancel"},
												 0);
		int selectedButton = dialog.open();
		
		// If the user hit cancel or closed the dialog, return true
		if ((selectedButton < 0) || selectedButton == 2) {
			return false;
		}
		
		// If they hit 'Yes', save the working copy 
		if (selectedButton == 0) {
			handleSavePressed();
		}
		
		return true;
	}

	/**
	 * Notification the 'new' button has been pressed
	 */
	protected void handleNewPressed() {
		
		// Take care of any unsaved changes
		if (!canReplaceWorkingCopy()) {
			return;
		}
		
		constructNewConfig();		
	}	
	
	/** 
	 * If a config type is selected, create a new config of that type initialized to 
	 * fWorkbenchSelection.  If a config is selected, create of new config of the
	 * same type as the selected config.
	 * protected void constructNewConfig() {
	 */
	protected void constructNewConfig() {	
		try {
			ILaunchConfigurationType type = null;

			Object obj = getTreeViewerFirstSelectedElement();
			if (obj instanceof ILaunchConfiguration) {
				type = ((ILaunchConfiguration)obj).getType();
			} else {
				type = (ILaunchConfigurationType)obj;
			}
			setChangesAreUserChanges(false);
			ILaunchConfigurationWorkingCopy wc = type.newInstance(null, generateUniqueNameFrom(DEFAULT_NEW_CONFIG_NAME));
			Object workbenchSelection = getContext();
			if (workbenchSelection != null) {
				wc.initializeDefaults(workbenchSelection);
			}
			setLastSavedName(null);
			setLaunchConfiguration(wc);
		} catch (CoreException ce) {
			DebugUIPlugin.errorDialog(getShell(), "Error", "Exception creating new launch configuration.", ce.getStatus());
 			clearLaunchConfiguration();
 			return;			
		}		
	}
	
	/**
	 * Notification the 'delete' button has been pressed
	 */
	protected void handleDeletePressed() {
		try {
			Object firstElement = getTreeViewerFirstSelectedElement();
			if (firstElement instanceof ILaunchConfiguration) {
				ILaunchConfiguration config = (ILaunchConfiguration) firstElement;
				clearLaunchConfiguration();
				config.delete();
			}
		} catch (CoreException ce) {
		}
	}	
	
	/**
	 * Notification the 'copy' button has been pressed
	 */
	protected void handleCopyPressed() {
		Object selectedElement = getTreeViewerFirstSelectedElement();
		if (selectedElement instanceof ILaunchConfiguration) {
			ILaunchConfiguration selectedConfig = (ILaunchConfiguration) selectedElement;
			String newName = generateUniqueNameFrom(selectedConfig.getName());
			try {
				ILaunchConfigurationWorkingCopy newWorkingCopy = selectedConfig.copy(newName);
				ILaunchConfigurationType configType = newWorkingCopy.getType();
				
				IStructuredSelection selection = new StructuredSelection(configType);
				setTreeViewerSelection(selection);
				setLaunchConfiguration(newWorkingCopy);
			} catch (CoreException ce) {				
			}			
		}
	}	
	
	/**
	 * Construct a new config name using the name of the given config as a starting point.
	 * The new name is guaranteed not to collide with any existing config name.
	 */
	protected String generateUniqueNameFrom(String startingName) {
		String newName = startingName;
		int index = 1;
		while (getLaunchManager().isExistingLaunchConfigurationName(newName)) {
			StringBuffer buffer = new StringBuffer(startingName);
			buffer.append('_');
			buffer.append(String.valueOf(index));
			index++;
			newName = buffer.toString();		
		}		
		return newName;
	}
	
	/**
	 * Notification the 'save & launch' button has been pressed
	 */
	protected void handleSaveAndLaunchPressed() {
		handleSavePressed();
		handleLaunchPressed();
	}
	
	/**
	 * Notification that the 'save' button has been pressed
	 */
	protected void handleSavePressed() {
		ILaunchConfigurationWorkingCopy workingCopy = getWorkingCopy();
		ILaunchConfiguration newConfig = null;
		setWorkingCopy(null);
		try {
			setIgnoreSelectionChanges(true);
			newConfig = workingCopy.doSave();
			setIgnoreSelectionChanges(false);
		} catch (CoreException ce) {			
		}	
		setLastSavedName(workingCopy.getName());	
		setWorkingCopyUserDirty(false);		
		setEnableStateEditButtons();
		
		getTreeViewer().setSelection(new StructuredSelection(newConfig));
	}
	
	/**
	 * Notification the 'launch' button has been pressed
	 */
	protected void handleLaunchPressed() {
		try {
			getWorkingCopy().launch(getMode());
		} catch (CoreException ce) {
		}		
		close();
	}
	
	protected IPreferenceStore getPreferenceStore() {
		return DebugUIPlugin.getDefault().getPreferenceStore();
	}

	/***************************************************************************************
	 * 
	 * ProgressMonitor & IRunnableContext related method
	 * 
	 ***************************************************************************************/

	/**
	 * @see IRunnableContext#run(boolean, boolean, IRunnableWithProgress)
	 */
	public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable) throws InvocationTargetException, InterruptedException {
		// The operation can only be canceled if it is executed in a separate thread.
		// Otherwise the UI is blocked anyway.
		Object state = aboutToStart(fork && cancelable);
		fActiveRunningOperations++;
		try {
			ModalContext.run(runnable, fork, fProgressMonitorPart, getShell().getDisplay());
		} finally {
			fActiveRunningOperations--;
			stopped(state);
		}
	}
	
	/**
	 * About to start a long running operation tiggered through
	 * the dialog. Shows the progress monitor and disables the dialog's
	 * buttons and controls.
	 *
	 * @param enableCancelButton <code>true</code> if the Cancel button should
	 *   be enabled, and <code>false</code> if it should be disabled
	 * @return the saved UI state
	 */
	private Object aboutToStart(boolean enableCancelButton) {
		Map savedState = null;
		if (getShell() != null) {
			// Save focus control
			Control focusControl = getShell().getDisplay().getFocusControl();
			if (focusControl != null && focusControl.getShell() != getShell())
				focusControl = null;
				
			getCancelButton().removeSelectionListener(fCancelListener);
			
			// Set the busy cursor to all shells.
			Display d = getShell().getDisplay();
			waitCursor = new Cursor(d, SWT.CURSOR_WAIT);
			setDisplayCursor(waitCursor);
					
			// Set the arrow cursor to the cancel component.
			arrowCursor= new Cursor(d, SWT.CURSOR_ARROW);
			getCancelButton().setCursor(arrowCursor);
	
			// Deactivate shell
			savedState = saveUIState(enableCancelButton);
			if (focusControl != null)
				savedState.put(FOCUS_CONTROL, focusControl);
				
			// Attach the progress monitor part to the cancel button
			getProgressMonitorPart().attachToCancelComponent(getCancelButton());
			getProgressMonitorPart().setVisible(true);
		}
		return savedState;
	}

	/**
	 * A long running operation triggered through the dialog
	 * was stopped either by user input or by normal end.
	 * Hides the progress monitor and restores the enable state
	 * dialog's buttons and controls.
	 *
	 * @param savedState the saved UI state as returned by <code>aboutToStart</code>
	 * @see #aboutToStart
	 */
	private void stopped(Object savedState) {
		if (getShell() != null) {
			getProgressMonitorPart().setVisible(false);	
			getProgressMonitorPart().removeFromCancelComponent(getCancelButton());
			Map state = (Map)savedState;
			restoreUIState(state);
			getCancelButton().addSelectionListener(fCancelListener);
	
			setDisplayCursor(null);	
			getCancelButton().setCursor(null);
			waitCursor.dispose();
			waitCursor = null;
			arrowCursor.dispose();
			arrowCursor = null;
			Control focusControl = (Control)state.get(FOCUS_CONTROL);
			if (focusControl != null)
				focusControl.setFocus();
		}
	}

	/**
	 * Captures and returns the enabled/disabled state of the wizard dialog's
	 * buttons and the tree of controls for the currently showing page. All
	 * these controls are disabled in the process, with the possible excepton of
	 * the Cancel button.
	 *
	 * @param keepCancelEnabled <code>true</code> if the Cancel button should
	 *   remain enabled, and <code>false</code> if it should be disabled
	 * @return a map containing the saved state suitable for restoring later
	 *   with <code>restoreUIState</code>
	 * @see #restoreUIState
	 */
	private Map saveUIState(boolean keepCancelEnabled) {
		Map savedState= new HashMap(10);
		saveEnableStateAndSet(getNewButton(), savedState, "new", false);//$NON-NLS-1$
		saveEnableStateAndSet(getDeleteButton(), savedState, "delete", false);//$NON-NLS-1$
		saveEnableStateAndSet(getCopyButton(), savedState, "copy", false);//$NON-NLS-1$
		saveEnableStateAndSet(getSaveButton(), savedState, "save", false);//$NON-NLS-1$
		saveEnableStateAndSet(getSaveAndLaunchButton(), savedState, "saveandlaunch", false);//$NON-NLS-1$
		saveEnableStateAndSet(getCancelButton(), savedState, "cancel", keepCancelEnabled);//$NON-NLS-1$
		saveEnableStateAndSet(getLaunchButton(), savedState, "launch", false);//$NON-NLS-1$
		TabItem selectedTab = getTabFolder().getItem(getTabFolder().getSelectionIndex());
		savedState.put("tab", ControlEnableState.disable(selectedTab.getControl()));//$NON-NLS-1$
		return savedState;
	}

	/**
	 * Saves the enabled/disabled state of the given control in the
	 * given map, which must be modifiable.
	 *
	 * @param w the control, or <code>null</code> if none
	 * @param h the map (key type: <code>String</code>, element type:
	 *   <code>Boolean</code>)
	 * @param key the key
	 * @param enabled <code>true</code> to enable the control, 
	 *   and <code>false</code> to disable it
	 * @see #restoreEnableStateAndSet
	 */
	private void saveEnableStateAndSet(Control w, Map h, String key, boolean enabled) {
		if (w != null) {
			h.put(key, new Boolean(w.isEnabled()));
			w.setEnabled(enabled);
		}
	}

	/**
	 * Restores the enabled/disabled state of the wizard dialog's
	 * buttons and the tree of controls for the currently showing page.
	 *
	 * @param state a map containing the saved state as returned by 
	 *   <code>saveUIState</code>
	 * @see #saveUIState
	 */
	private void restoreUIState(Map state) {
		restoreEnableState(getNewButton(), state, "new");//$NON-NLS-1$
		restoreEnableState(getDeleteButton(), state, "delete");//$NON-NLS-1$
		restoreEnableState(getCopyButton(), state, "copy");//$NON-NLS-1$
		restoreEnableState(getSaveButton(), state, "save");//$NON-NLS-1$
		restoreEnableState(getSaveAndLaunchButton(), state, "saveandlaunch");//$NON-NLS-1$
		restoreEnableState(getCancelButton(), state, "cancel");//$NON-NLS-1$
		restoreEnableState(getLaunchButton(), state, "launch");//$NON-NLS-1$
		ControlEnableState tabState = (ControlEnableState) state.get("tab");//$NON-NLS-1$
		tabState.restore();
	}

	/**
	 * Restores the enabled/disabled state of the given control.
	 *
	 * @param w the control
	 * @param h the map (key type: <code>String</code>, element type:
	 *   <code>Boolean</code>)
	 * @param key the key
	 * @see #saveEnableStateAndSet
	 */
	private void restoreEnableState(Control w, Map h, String key) {
		if (w != null) {
			Boolean b = (Boolean) h.get(key);
			if (b != null)
				w.setEnabled(b.booleanValue());
		}
	}

	/**
	 * Sets the given cursor for all shells currently active
	 * for this window's display.
	 *
	 * @param c the cursor
	 */
	private void setDisplayCursor(Cursor c) {
		Shell[] shells = getShell().getDisplay().getShells();
		for (int i = 0; i < shells.length; i++)
			shells[i].setCursor(c);
	}
	
	/**
	 * @see Dialog#cancelPressed()
	 */
	protected void cancelPressed() {
		if (fActiveRunningOperations <= 0) {
			super.cancelPressed();
		} else {
			getCancelButton().setEnabled(false);
		}
	}

	/**
	 * Checks whether it is alright to close this dialog
	 * and performed standard cancel processing. If there is a
	 * long running operation in progress, this method posts an
	 * alert message saying that the dialog cannot be closed.
	 * 
	 * @return <code>true</code> if it is alright to close this dialog, and
	 *  <code>false</code> if it is not
	 */
	private boolean okToClose() {
		if (fActiveRunningOperations > 0) {
			synchronized (this) {
				fWindowClosingDialog = createDialogClosingDialog();
			}	
			fWindowClosingDialog.open();
			synchronized (this) {
				fWindowClosingDialog = null;
			}
			return false;
		}
		
		return true;
	}

	/**
	 * Creates and return a new wizard closing dialog without opening it.
	 */ 
	private MessageDialog createDialogClosingDialog() {
		MessageDialog result= new MessageDialog(
			getShell(),
			JFaceResources.getString("WizardClosingDialog.title"),//$NON-NLS-1$
			null,
			JFaceResources.getString("WizardClosingDialog.message"),//$NON-NLS-1$
			MessageDialog.QUESTION,
			new String[] {IDialogConstants.OK_LABEL},
			0 ); 
		return result;
	}
}


