/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.editor.composites;

import static org.eclipse.m2e.core.ui.internal.editing.PomEdits.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.index.IndexedArtifactFile;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectManager;
import org.eclipse.m2e.core.ui.internal.dialogs.EditDependencyDialog;
import org.eclipse.m2e.core.ui.internal.dialogs.MavenRepositorySearchDialog;
import org.eclipse.m2e.core.ui.internal.editing.PomHelper;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.Operation;
import org.eclipse.m2e.core.ui.internal.editing.PomEdits.OperationTuple;
import org.eclipse.m2e.editor.MavenEditorImages;
import org.eclipse.m2e.editor.MavenEditorPlugin;
import org.eclipse.m2e.editor.dialogs.ManageDependenciesDialog;
import org.eclipse.m2e.editor.internal.Messages;
import org.eclipse.m2e.editor.pom.MavenPomEditor;
import org.eclipse.m2e.editor.pom.MavenPomEditorPage;
import org.eclipse.m2e.editor.pom.SearchControl;
import org.eclipse.m2e.editor.pom.SearchMatcher;
import org.eclipse.m2e.editor.pom.ValueProvider;
import org.eclipse.m2e.model.edit.pom.Dependency;
import org.eclipse.m2e.model.edit.pom.DependencyManagement;
import org.eclipse.m2e.model.edit.pom.Model;
import org.eclipse.m2e.model.edit.pom.PomPackage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * @author Eugene Kuleshov
 */
public class DependenciesComposite extends Composite {
  private static final Logger log = LoggerFactory.getLogger(DependenciesComposite.class);

  protected static PomPackage POM_PACKAGE = PomPackage.eINSTANCE;

  protected MavenPomEditorPage editorPage;

  MavenPomEditor pomEditor;

  private FormToolkit toolkit = new FormToolkit(Display.getCurrent());

  // controls

  PropertiesListComposite<Dependency> dependencyManagementEditor;

  //This ListComposite takes both m2e and maven Dependencies
  DependenciesListComposite<Object> dependenciesEditor;
  
  private final List<String> temporaryRemovedDependencies = new ArrayList<String>();

  Button dependencySelectButton;

  Action dependencySelectAction;

  SearchControl searchControl;

  SearchMatcher searchMatcher;

  DependencyFilter searchFilter;

  Action openWebPageAction;

  // model

  Model model;

  final DependencyLabelProvider dependencyLabelProvider = new DependencyLabelProvider(true);

  final DependencyLabelProvider dependencyManagementLabelProvider = new DependencyLabelProvider();

  protected boolean showInheritedDependencies = false;

  final ListEditorContentProvider<Object> dependenciesContentProvider = new ListEditorContentProvider<Object>();

  DependenciesComparator<Object> dependenciesComparator;

  final ListEditorContentProvider<Dependency> dependencyManagementContentProvider = new ListEditorContentProvider<Dependency>();

  DependenciesComparator<Dependency> dependencyManagementComparator;

  public DependenciesComposite(Composite composite, MavenPomEditorPage editorPage, int flags, MavenPomEditor pomEditor) {
    super(composite, flags);
    this.editorPage = editorPage;
    this.pomEditor = pomEditor;
    createComposite();
    editorPage.initPopupMenu(dependenciesEditor.getViewer(), ".dependencies"); //$NON-NLS-1$
    editorPage.initPopupMenu(dependencyManagementEditor.getViewer(), ".dependencyManagement"); //$NON-NLS-1$
  }

  private void createComposite() {
    GridLayout gridLayout = new GridLayout();
    gridLayout.makeColumnsEqualWidth = true;
    gridLayout.marginWidth = 0;
    setLayout(gridLayout);
    toolkit.adapt(this);

    SashForm horizontalSash = new SashForm(this, SWT.NONE);
    GridData horizontalCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    horizontalCompositeGridData.heightHint = 200;
    horizontalSash.setLayoutData(horizontalCompositeGridData);
    toolkit.adapt(horizontalSash, true, true);

    createDependenciesSection(horizontalSash);
    createDependencyManagementSection(horizontalSash);

    horizontalSash.setWeights(new int[] {1, 1});
  }

  private void createDependenciesSection(SashForm verticalSash) {
    Section dependenciesSection = toolkit.createSection(verticalSash, ExpandableComposite.TITLE_BAR);
    dependenciesSection.marginWidth = 3;
    dependenciesSection.setText(Messages.DependenciesComposite_sectionDependencies);
    
    dependenciesComparator = new DependenciesComparator<Object>();
    dependenciesContentProvider.setComparator(dependenciesComparator);

    dependenciesEditor = new DependenciesListComposite<Object>(dependenciesSection, SWT.NONE, true);
    dependenciesEditor.setCellLabelProvider(new DelegatingStyledCellLabelProvider( dependencyLabelProvider));
    dependenciesEditor.setContentProvider(dependenciesContentProvider);

    dependenciesEditor.setRemoveButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        final List<Object> dependencyList = dependenciesEditor.getSelection();
        try {
          performOnDOMDocument(new OperationTuple(editorPage.getPomEditor().getDocument(), new Operation() {
            public void process(Document document) {
              Element deps = findChild(document.getDocumentElement(), DEPENDENCIES);
              if (deps == null) {
                //TODO log
                return;
              }
              for (Object dependency : dependencyList) {
                if (dependency instanceof Dependency) {
                  Element dep = findChild(deps, DEPENDENCY, 
                    childEquals(GROUP_ID, ((Dependency)dependency).getGroupId()), 
                    childEquals(ARTIFACT_ID, ((Dependency)dependency).getArtifactId()));
                  removeChild(deps, dep);
                }
              }
              removeIfNoChildElement(deps);
            }
          }));
        } catch (Exception x) {
          log.error("error removing dependencies", x);
        }
        setDependenciesInput();
      }
    });

    dependenciesEditor.setPropertiesListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        Object selection = dependenciesEditor.getSelection().get(0);
        if (selection instanceof Dependency) {
          Dependency dependency = (Dependency) selection;
          EditDependencyDialog d = new EditDependencyDialog(getShell(), false, editorPage
              .getProject(), editorPage.getPomEditor().getMavenProject(), editorPage.getPomEditor().getDocument());
          d.setDependency(dependency);
          if(d.open() == Window.OK) {
            setDependenciesInput();
            dependenciesEditor.setSelection(Collections.singletonList((Object) dependency));
          }
        } else if (selection instanceof org.apache.maven.model.Dependency) {
          /*
           * TODO: Support editing or displaying of inherited/managed dependencies.
           */
        }
      }
    });

    dependenciesSection.setClient(dependenciesEditor);
    toolkit.adapt(dependenciesEditor);
    toolkit.paintBordersFor(dependenciesEditor);

    dependenciesEditor.setManageButtonListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        try {
          openManageDependenciesDialog();
        } catch(InvocationTargetException e1) {
          MavenEditorPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, MavenEditorPlugin.PLUGIN_ID, "Error: ", e1)); //$NON-NLS-1$
        } catch(InterruptedException e1) {
          MavenEditorPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, MavenEditorPlugin.PLUGIN_ID, "Error: ", e1)); //$NON-NLS-1$
        }
      }
    });
    
    dependenciesEditor.setAddButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        final MavenRepositorySearchDialog addDepDialog = MavenRepositorySearchDialog.createSearchDependencyDialog(
            getShell(), Messages.DependenciesComposite_action_selectDependency, editorPage.getPomEditor().getMavenProject(), editorPage.getProject(), false);
        
        if(addDepDialog.open() == Window.OK) {
          IndexedArtifactFile dep = (IndexedArtifactFile) addDepDialog.getFirstResult();
          final org.apache.maven.model.Dependency dependency = dep.getDependency();
          String selectedScope = addDepDialog.getSelectedScope();
          dependency.setScope(selectedScope);
          
          if (dep.version == null) {
            dependency.setVersion(null);
          }
          try {
            performOnDOMDocument(new OperationTuple(editorPage.getPomEditor().getDocument(), new Operation() {
              public void process(Document document) {
                Element depsEl = getChild(document.getDocumentElement(), DEPENDENCIES);
                PomHelper.addOrUpdateDependency(depsEl, 
                    dependency.getGroupId(), dependency.getArtifactId(), 
                    dependency.getVersion(), dependency.getType(), dependency.getScope(), dependency.getClassifier());
              }
            }));
          } catch(Exception e1) {
            log.error("errror adding dependency", e1);
          }
          
          setDependenciesInput();
          if (model.getDependencies() != null && model.getDependencies().size() > 0) {
            dependenciesEditor.setSelection(Collections.<Object>singletonList(model.getDependencies().get(model.getDependencies().size() - 1)));
          }
        }
      }

    });

    ToolBarManager modulesToolBarManager = new ToolBarManager(SWT.FLAT);
    
    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_sortAlphabetically, MavenEditorImages.SORT) {
      {
        setChecked(false);
      }
      
      @Override
      public int getStyle() {
        return AS_CHECK_BOX;
      }
      
      @Override
      public void run() {
        dependenciesContentProvider.setShouldSort(isChecked());
        dependenciesEditor.getViewer().refresh();
      }
    });
    
    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_showInheritedDependencies, 
        MavenEditorImages.SHOW_INHERITED_DEPENDENCIES) {
      {
        setChecked(false);
      }
      
      @Override
      public int getStyle() {
        return AS_CHECK_BOX;
      }
      
      @Override
      public void run() {
        if (isChecked()) {
          showInheritedDependencies  = true;
        } else {
          showInheritedDependencies  = false;
        }
        ISelection selection = dependenciesEditor.getViewer().getSelection();
        setDependenciesInput();
        dependenciesEditor.getViewer().refresh();
        dependenciesEditor.getViewer().setSelection(selection, true);
      }
    });
    
    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_showgroupid,
        MavenEditorImages.SHOW_GROUP) {
      {
        setChecked(false);
        dependenciesComparator.setSortByGroups(false);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        dependencyLabelProvider.setShowGroupId(isChecked());
        dependenciesComparator.setSortByGroups(isChecked());
        dependenciesEditor.getViewer().refresh();
      }
    });

    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_filter, MavenEditorImages.FILTER) {
      {
        setChecked(true);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        TableViewer viewer = dependenciesEditor.getViewer();
        if(isChecked()) {
          viewer.addFilter(searchFilter);
        } else {
          viewer.removeFilter(searchFilter);
        }
        viewer.refresh();
        if(isChecked()) {
          searchControl.getSearchText().setFocus();
        }
      }
    });

    Composite toolbarComposite = toolkit.createComposite(dependenciesSection);
    GridLayout toolbarLayout = new GridLayout(1, true);
    toolbarLayout.marginHeight = 0;
    toolbarLayout.marginWidth = 0;
    toolbarComposite.setLayout(toolbarLayout);
    toolbarComposite.setBackground(null);

    modulesToolBarManager.createControl(toolbarComposite);
    dependenciesSection.setTextClient(toolbarComposite);
  }

  private void createDependencyManagementSection(SashForm verticalSash) {
    Section dependencyManagementSection = toolkit.createSection(verticalSash, ExpandableComposite.TITLE_BAR);
    dependencyManagementSection.marginWidth = 3;
    dependencyManagementSection.setText(Messages.DependenciesComposite_sectionDependencyManagement);
    dependencyManagementComparator = new DependenciesComparator<Dependency>();
    dependencyManagementContentProvider.setComparator(dependencyManagementComparator);

    dependencyManagementEditor = new PropertiesListComposite<Dependency>(dependencyManagementSection, SWT.NONE, true);
    dependencyManagementEditor.setContentProvider(dependencyManagementContentProvider);
    dependencyManagementEditor.setLabelProvider(dependencyManagementLabelProvider);
    dependencyManagementSection.setClient(dependencyManagementEditor);
    

    dependencyManagementEditor.setRemoveButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        final List<Dependency> dependencyList = dependencyManagementEditor.getSelection();
        try {
          performOnDOMDocument(new OperationTuple(editorPage.getPomEditor().getDocument(), new Operation() {
            public void process(Document document) {
              Element deps = findChild(findChild(document.getDocumentElement(), DEPENDENCY_MANAGEMENT), DEPENDENCIES);
              if (deps == null) {
                //TODO log
                return;
              }
              for (Dependency dependency : dependencyList) {
                Element dep = findChild(deps, DEPENDENCY, 
                    childEquals(GROUP_ID, dependency.getGroupId()), 
                    childEquals(ARTIFACT_ID, dependency.getArtifactId()));
                removeChild(deps, dep);
              }
              removeIfNoChildElement(deps);
            }
          }));
        } catch (Exception x) {
          log.error("error removing dependencies", x);
        }
      }
    });
    
    dependencyManagementEditor.setPropertiesListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        Dependency dependency = dependencyManagementEditor.getSelection().get(0);
        EditDependencyDialog d = new EditDependencyDialog(getShell(), true, editorPage
            .getProject(), editorPage.getPomEditor().getMavenProject(), editorPage.getPomEditor().getDocument());
        d.setDependency(dependency);
        if(d.open() == Window.OK) {
          setDependencyManagementInput();
          dependencyManagementEditor.setSelection(Collections.singletonList(dependency));
          //refresh this one to update decorations..
          dependenciesEditor.refresh();
        }
      }
    });

    dependencyManagementEditor.addSelectionListener(new ISelectionChangedListener() {
      public void selectionChanged(SelectionChangedEvent event) {
        List<Dependency> selection = dependencyManagementEditor.getSelection();

        if(!selection.isEmpty()) {
          dependenciesEditor.setSelection(Collections.<Object> emptyList());
        }
      }
    });

    toolkit.adapt(dependencyManagementEditor);
    toolkit.paintBordersFor(dependencyManagementEditor);

    dependencyManagementEditor.setAddButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        final MavenRepositorySearchDialog addDepDialog = MavenRepositorySearchDialog.createSearchDependencyDialog(
            getShell(), Messages.DependenciesComposite_action_selectDependency, editorPage.getPomEditor().getMavenProject(), editorPage.getProject(), true);
        if(addDepDialog.open() == Window.OK) {
          IndexedArtifactFile dep = (IndexedArtifactFile) addDepDialog.getFirstResult();
          final org.apache.maven.model.Dependency dependency = dep.getDependency();
          String selectedScope = addDepDialog.getSelectedScope();
          dependency.setScope(selectedScope);
          if (dep.version == null) {
            dependency.setVersion(null);
          }
          
          try {
            performOnDOMDocument(new OperationTuple(editorPage.getPomEditor().getDocument(), new Operation() {
              public void process(Document document) {
                Element depsEl = getChild(document.getDocumentElement(), DEPENDENCY_MANAGEMENT, DEPENDENCIES);
                PomHelper.addOrUpdateDependency(depsEl, 
                    dependency.getGroupId(), dependency.getArtifactId(), 
                    dependency.getVersion(), dependency.getType(), dependency.getScope(), dependency.getClassifier());
              }
            }));
          } catch(Exception e1) {
            log.error("errror adding dependency", e1);
          }
          setDependenciesInput();
          setDependencyManagementInput();
          if (model.getDependencyManagement() != null && model.getDependencyManagement().getDependencies() != null 
              && model.getDependencyManagement().getDependencies().size() > 0) {
            List<Dependency> dlist = model.getDependencyManagement().getDependencies();
            dependencyManagementEditor.setSelection(Collections.<Dependency>singletonList(dlist.get(dlist.size() - 1)));
          }
          
          //refresh this one to update decorations..
          dependenciesEditor.refresh();
        }
      }
    });

    ToolBarManager modulesToolBarManager = new ToolBarManager(SWT.FLAT);

    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_sortAlphabetically, MavenEditorImages.SORT) {
      {
        setChecked(false);
        dependencyManagementContentProvider.setShouldSort(false);
      }
      
      @Override
      public int getStyle() {
        return AS_CHECK_BOX;
      }
      
      @Override
      public void run() {
        dependencyManagementContentProvider.setShouldSort(isChecked());
        dependencyManagementEditor.getViewer().refresh();
      }
    });
    
    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_showgroupid,
        MavenEditorImages.SHOW_GROUP) {
      {
        setChecked(false);
        dependencyManagementComparator.setSortByGroups(false);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        dependencyManagementLabelProvider.setShowGroupId(isChecked());
        dependencyManagementComparator.setSortByGroups(isChecked());
        dependencyManagementEditor.getViewer().refresh();
      }
    });

    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_filter, MavenEditorImages.FILTER) {
      {
        setChecked(true);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        TableViewer viewer = dependencyManagementEditor.getViewer();
        if(isChecked()) {
          viewer.addFilter(searchFilter);
        } else {
          viewer.removeFilter(searchFilter);
        }
        viewer.refresh();
        if(isChecked()) {
          searchControl.getSearchText().setFocus();
        }
      }
    });

    Composite toolbarComposite = toolkit.createComposite(dependencyManagementSection);
    GridLayout toolbarLayout = new GridLayout(1, true);
    toolbarLayout.marginHeight = 0;
    toolbarLayout.marginWidth = 0;
    toolbarComposite.setLayout(toolbarLayout);
    toolbarComposite.setBackground(null);

    modulesToolBarManager.createControl(toolbarComposite);
    dependencyManagementSection.setTextClient(toolbarComposite);
  }


  @SuppressWarnings("unchecked")
  public void loadData(Model model, ValueProvider<DependencyManagement> dependencyManagementProvider) {
    this.model = model;
    this.dependencyLabelProvider.setPomEditor(editorPage.getPomEditor(), dependencyManagementProvider);
    this.dependencyManagementLabelProvider.setPomEditor(editorPage.getPomEditor(), dependencyManagementProvider);

    setDependenciesInput();

    DependencyManagement dependencyManagement = model.getDependencyManagement();
    dependencyManagementEditor.setInput(dependencyManagement == null ? null : dependencyManagement.getDependencies());

    dependenciesEditor.setReadOnly(editorPage.isReadOnly());
    dependencyManagementEditor.setReadOnly(editorPage.isReadOnly());

    if(searchControl != null) {
      searchControl.getSearchText().setEditable(true);
    }
  }

  public void updateView(final MavenPomEditorPage editorPage, final Notification notification) {
        EObject object = (EObject) notification.getNotifier();
        Object feature = notification.getFeature();

        // XXX event is not received when <dependencies> is deleted in XML
        if(object instanceof Model) {
          Model model2 = (Model) object;
          if((model2.getDependencyManagement() != null && dependencyManagementEditor.getInput() == null) 
              || feature == PomPackage.Literals.MODEL__DEPENDENCY_MANAGEMENT) {
            dependencyManagementEditor.setInput(model2.getDependencyManagement() != null ? model2.getDependencyManagement().getDependencies() : null);
          } else if(model2.getDependencyManagement() == null) {
            dependencyManagementEditor.setInput(null);
          }

          if((model2.getDependencies() != null && dependenciesEditor.getInput() == null) 
              || feature == PomPackage.Literals.MODEL__DEPENDENCIES) {
            setDependenciesInput();
          } else if(model2.getDependencies() == null) {
            setDependenciesInput();
          }
          dependenciesEditor.refresh();
          dependencyManagementEditor.refresh();
        }

        if(object instanceof DependencyManagement) {
          if(dependencyManagementEditor.getInput() == null || feature == PomPackage.Literals.DEPENDENCY_MANAGEMENT__DEPENDENCIES) {
            dependencyManagementEditor.setInput(((DependencyManagement) object).getDependencies());
          }
          dependencyManagementEditor.refresh();
          //refresh this one to update decorations..
          dependenciesEditor.refresh();
        }
        if (object instanceof Dependency) {
          dependenciesEditor.refresh();
          dependencyManagementEditor.refresh();
        }
  }

  public void setSearchControl(SearchControl searchControl) {
    if(this.searchControl != null) {
      return;
    }

    this.searchMatcher = new SearchMatcher(searchControl);
    this.searchFilter = new DependencyFilter(searchMatcher);
    this.searchControl = searchControl;
    this.searchControl.getSearchText().addModifyListener(new ModifyListener() {
      public void modifyText(ModifyEvent e) {
        selectDepenendencies(dependenciesEditor, model, POM_PACKAGE.getModel_Dependencies());
        EObject parent = model.getDependencyManagement() != null ? model.getDependencyManagement() : null;
        selectDepenendencies(dependencyManagementEditor, parent,
            POM_PACKAGE.getDependencyManagement_Dependencies());
      }

      @SuppressWarnings({"unchecked", "rawtypes"})
      private void selectDepenendencies(PropertiesListComposite<?> dependencyManagementEditor, EObject parent,
          EStructuralFeature feature) {
        if(parent != null) {
          dependencyManagementEditor.setSelection((List) parent.eGet(feature));
          dependencyManagementEditor.refresh();
        }
      }
    });
    //we add filter here as the default behaviour is to filter..
    TableViewer viewer = dependenciesEditor.getViewer();
    viewer.addFilter(searchFilter);
    viewer = dependencyManagementEditor.getViewer();
    viewer.addFilter(searchFilter);

  }

  /** mkleint: apparently this methods shall find the version in resolved pom for the given dependency
   * not sure if getBaseVersion is the way to go..
   * Note: duplicated in DependencyDetailsComposite 
   * @param groupId
   * @param artifactId
   * @param monitor
   * @return
   */
  String getVersion(String groupId, String artifactId, IProgressMonitor monitor) {
    try {
      MavenProject mavenProject = editorPage.getPomEditor().readMavenProject(false, monitor);
      Artifact a = mavenProject.getArtifactMap().get(groupId + ":" + artifactId); //$NON-NLS-1$
      if(a != null) {
        return a.getBaseVersion();
      }
    } catch(CoreException ex) {
      log.error(ex.getMessage(), ex);
    }
    return null;
  }

  public static class DependencyFilter extends ViewerFilter {
    private SearchMatcher searchMatcher;

    public DependencyFilter(SearchMatcher searchMatcher) {
      this.searchMatcher = searchMatcher;
    }

    public boolean select(Viewer viewer, Object parentElement, Object element) {
      if(element instanceof Dependency) {
        Dependency d = (Dependency) element;
        return searchMatcher.isMatchingArtifact(d.getGroupId(), d.getArtifactId());
      } else if (element instanceof org.apache.maven.model.Dependency) {
        org.apache.maven.model.Dependency dependency = (org.apache.maven.model.Dependency) element;
        return searchMatcher.isMatchingArtifact(dependency.getGroupId(), dependency.getArtifactId());
      }
      return false;
    }
  }

  void openManageDependenciesDialog() throws InvocationTargetException, InterruptedException {
    /*
     * A linked list representing the path from child to root parent pom.
     * The head is the child, the tail is the root pom
     */
    final LinkedList<MavenProject> hierarchy = new LinkedList<MavenProject>();
    
    IRunnableWithProgress projectLoader = new IRunnableWithProgress() {
      public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
        try {
          MavenProjectManager projectManager = MavenPlugin.getDefault().getMavenProjectManager();
          IMavenProjectFacade projectFacade = projectManager.create(pomEditor.getPomFile(), true, monitor);
          if (projectFacade != null) {
            hierarchy.addAll(new ParentGatherer(projectFacade.getMavenProject(), projectFacade).getParentHierarchy(monitor));
          }
        } catch(CoreException e) {
          throw new InvocationTargetException(e);
        }
      }
    };

    PlatformUI.getWorkbench().getProgressService().run(false, true, projectLoader);

    if (hierarchy.isEmpty()) {
      //We were unable to read the project metadata above, so there was an error. 
      //User has already been notified to fix the problem.
      return;
    }
    
    final ManageDependenciesDialog manageDepDialog = new ManageDependenciesDialog(getShell(), model, hierarchy,
        dependenciesEditor.getSelection());
    manageDepDialog.open();
  }
  
  protected void setDependencyManagementInput() {
    DependencyManagement dependencyManagement = model.getDependencyManagement();
    dependencyManagementEditor.setInput(dependencyManagement == null ? null : dependencyManagement.getDependencies());
  }

  protected void setDependenciesInput() {
    List<Object> deps = new ArrayList<Object>();
    if (model.getDependencies() != null) {
      deps.addAll(model.getDependencies());
    }
    if (showInheritedDependencies) {
      
      /*
       * Add the inherited dependencies into the bunch. But don't we need to
       * filter out the dependencies that are duplicated in the M2E model, so
       * we need to run through each list and only add ones that aren't in both.
       */
      List<org.apache.maven.model.Dependency> allDeps = new LinkedList<org.apache.maven.model.Dependency>();
      MavenProject mp = pomEditor.getMavenProject();
      if (mp != null) {
        allDeps.addAll(mp.getDependencies());
      }
      for (org.apache.maven.model.Dependency mavenDep : allDeps) {
        boolean found = false;
        Iterator<Dependency> iter = model.getDependencies().iterator();
        while (!found && iter.hasNext()) {
          Dependency m2eDep = iter.next();
          if (mavenDep.getGroupId().equals(m2eDep.getGroupId()) 
              && mavenDep.getArtifactId().equals(m2eDep.getArtifactId())) {
            found = true;
          }
        }
        if (!found) {
          //now check the temporary keys
          if (!temporaryRemovedDependencies.contains(mavenDep.getGroupId() + ":" + mavenDep.getArtifactId())) {
            deps.add(mavenDep);
          }
        }
      }
    }
    dependenciesEditor.setInput(deps);
  }
  
  protected class PropertiesListComposite<T> extends ListEditorComposite<T> {
    private static final String PROPERTIES_BUTTON_KEY = "PROPERTIES"; //$NON-NLS-1$
    protected Button properties;
 
    public PropertiesListComposite(Composite parent, int style, boolean includeSearch) {
      super(parent, style, includeSearch);
    }
 
    @Override
    protected void createButtons(boolean includeSearch) {
      if(includeSearch) {
        createAddButton();
      }
      createRemoveButton();
      properties = createButton(Messages.ListEditorComposite_btnProperties);
      addButton(PROPERTIES_BUTTON_KEY, properties);
    }
 
    public void setPropertiesListener(SelectionListener listener) {
      properties.addSelectionListener(listener);
    }
 
    @Override
    protected void viewerSelectionChanged() {
      super.viewerSelectionChanged();
      updatePropertiesButton();
    }
 
    protected void updatePropertiesButton() {
      boolean enable = !viewer.getSelection().isEmpty() && !isBadSelection();
      properties.setEnabled(!readOnly && enable);
    }
     
    @Override
    protected void updateRemoveButton() {
      boolean enable = !viewer.getSelection().isEmpty() && !isBadSelection();
      getRemoveButton().setEnabled(!readOnly && enable);
    }
     
    @Override
    public void setReadOnly(boolean readOnly) {
      super.setReadOnly(readOnly);
      updatePropertiesButton();
    }
     
    /**
     * Returns true if the viewer has no input or if there is currently
     * an inherited dependency selected
     * @return
     */
    protected boolean isBadSelection() {
      @SuppressWarnings("unchecked")
      List<Object> deps = (List<Object>) viewer.getInput();
      if (deps == null || deps.isEmpty()) {
        return true;
      }
      boolean bad = false;
      IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
      @SuppressWarnings("unchecked")
      Iterator<Object> iter = selection.iterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        if (obj instanceof org.apache.maven.model.Dependency) {
          bad = true;
          break;
        }
      }
      return bad;
    }
  }
  
  protected class DependenciesListComposite<T> extends PropertiesListComposite<T> {

    private static final String MANAGE = "MANAGE"; //$NON-NLS-1$
    protected Button manage;

    public DependenciesListComposite(Composite parent, int style, boolean includeSearch) {
      super(parent, style, includeSearch);
    }
    

    @Override
    protected void createButtons(boolean includeSearch) {
      super.createButtons(includeSearch);
      manage = createButton(Messages.DependenciesComposite_manageButton);
      addButton(MANAGE, manage);
    }
    
    @Override
    protected void viewerSelectionChanged() {
      super.viewerSelectionChanged();
      updateManageButton();
    }
    
    @Override
    public void setReadOnly(boolean readOnly) {
      super.setReadOnly(readOnly);
      updateManageButton();
    }
    
    @Override
    public void refresh() {
      super.refresh();
      updateManageButton();
    }

    protected void updateManageButton() {
      boolean hasNonManaged = false;
      //MNGECLIPSE-2675 only enable when there are unmanaged dependencies
      if (model.getDependencies() != null) {
        for (Dependency d : model.getDependencies()) {
          if (d.getVersion() != null) {
            hasNonManaged = true;
            break;
          }
        }
      }
      manage.setEnabled(!readOnly && hasNonManaged);
    }
    
    public void setManageButtonListener(SelectionListener listener) {
      manage.addSelectionListener(listener);
    }
  }

  public void mavenProjectHasChanged() {
    temporaryRemovedDependencies.clear();
    //MNGECLIPSE-2673 when maven project changes and we show the inherited items, update now..
    if (showInheritedDependencies) {
      setDependenciesInput();
    }
    dependenciesEditor.refresh();
  }
  

  
}
