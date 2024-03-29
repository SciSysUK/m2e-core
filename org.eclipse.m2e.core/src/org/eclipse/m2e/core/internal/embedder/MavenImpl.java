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

package org.eclipse.m2e.core.internal.embedder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.MutablePlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.lifecycle.internal.DependencyContext;
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.version.DefaultPluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.properties.internal.EnvironmentUtils;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.DefaultSettingsProblem;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.building.SettingsProblem.Severity;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.settings.io.SettingsWriter;
import org.apache.maven.wagon.proxy.ProxyInfo;

import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.transfer.ArtifactNotFoundException;
import org.sonatype.aether.transfer.TransferListener;
import org.sonatype.aether.util.FilterRepositorySystemSession;

import org.eclipse.m2e.core.embedder.ILocalRepositoryListener;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.embedder.IMavenConfigurationChangeListener;
import org.eclipse.m2e.core.embedder.ISettingsChangeListener;
import org.eclipse.m2e.core.embedder.MavenConfigurationChangeEvent;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.internal.MavenPluginActivator;
import org.eclipse.m2e.core.internal.Messages;
import org.eclipse.m2e.core.internal.preferences.MavenPreferenceConstants;


public class MavenImpl implements IMaven, IMavenConfigurationChangeListener {
  private static final Logger log = LoggerFactory.getLogger(MavenImpl.class);

  /**
   * Id of maven core class realm
   */
  public static final String MAVEN_CORE_REALM_ID = "plexus.core"; //$NON-NLS-1$

  private DefaultPlexusContainer plexus;

  private final IMavenConfiguration mavenConfiguration;

  private final ConverterLookup converterLookup = new DefaultConverterLookup();

  private final ArrayList<ISettingsChangeListener> settingsListeners = new ArrayList<ISettingsChangeListener>();

  private final ArrayList<ILocalRepositoryListener> localRepositoryListeners = new ArrayList<ILocalRepositoryListener>();

  /**
   * Cached parsed settings.xml instance
   */
  private Settings settings;

  public MavenImpl(IMavenConfiguration mavenConfiguration) {
    this.mavenConfiguration = mavenConfiguration;
    mavenConfiguration.addConfigurationChangeListener(this);
  }

  public MavenExecutionRequest createExecutionRequest(IProgressMonitor monitor) throws CoreException {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();

    // this causes problems with unexpected "stale project configuration" error markers
    // need to think how to manage ${maven.build.timestamp} properly inside workspace
    //request.setStartTime( new Date() );

    if(mavenConfiguration.getGlobalSettingsFile() != null) {
      request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
    }
    if(mavenConfiguration.getUserSettingsFile() != null) {
      request.setUserSettingsFile(new File(mavenConfiguration.getUserSettingsFile()));
    }

    try {
      lookup(MavenExecutionRequestPopulator.class).populateFromSettings(request, getSettings());
    } catch(MavenExecutionRequestPopulationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_no_exec_req, ex));
    }

    ArtifactRepository localRepository = getLocalRepository();
    request.setLocalRepository(localRepository);
    request.setLocalRepositoryPath(localRepository.getBasedir());
    request.setOffline(mavenConfiguration.isOffline());

    // logging
    request.setTransferListener(createArtifactTransferListener(monitor));

    request.getUserProperties().put("m2e.version", MavenPluginActivator.getVersion()); //$NON-NLS-1$

    EnvironmentUtils.addEnvVars(request.getSystemProperties());
    request.getSystemProperties().putAll(System.getProperties());

    request.setCacheNotFound(true);
    request.setCacheTransferError(true);

    // the right way to disable snapshot update
    // request.setUpdateSnapshots(false);
    return request;
  }

  public String getLocalRepositoryPath() {
    String path = null;
    try {
      Settings settings = getSettings();
      path = settings.getLocalRepository();
    } catch(CoreException ex) {
      // fall through
    }
    if(path == null) {
      path = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
    }
    return path;
  }

  public MavenExecutionResult execute(MavenExecutionRequest request, IProgressMonitor monitor) {
    // XXX is there a way to set per-request log level?

    MavenExecutionResult result;
    try {
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
      result = lookup(Maven.class).execute(request);
    } catch(MavenExecutionRequestPopulationException ex) {
      result = new DefaultMavenExecutionResult();
      result.addException(ex);
    } catch(Exception e) {
      result = new DefaultMavenExecutionResult();
      result.addException(e);
    }
    return result;
  }

  public MavenSession createSession(MavenExecutionRequest request, MavenProject project) {
    RepositorySystemSession repoSession = createRepositorySession(request);
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    MavenSession mavenSession = new MavenSession(plexus, repoSession, request, result);
    if(project != null) {
      mavenSession.setProjects(Collections.singletonList(project));
    }
    return mavenSession;
  }

  private RepositorySystemSession createRepositorySession(MavenExecutionRequest request) {
    try {
      RepositorySystemSession session = ((DefaultMaven) lookup(Maven.class)).newRepositorySession(request);
      final String updatePolicy = mavenConfiguration.getGlobalUpdatePolicy();
      if (!request.isUpdateSnapshots() && updatePolicy != null) {
        session = new FilterRepositorySystemSession(session) {
          public String getUpdatePolicy() {
            return updatePolicy;
          }
        };
      }
      return session;
    } catch(CoreException ex) {
      log.error(ex.getMessage(), ex);
      throw new IllegalStateException("Could not look up Maven embedder", ex);
    }
  }

  public void execute(MavenSession session, MojoExecution execution, IProgressMonitor monitor) {
    Map<MavenProject, Set<Artifact>> artifacts = new HashMap<MavenProject, Set<Artifact>>();
    for(MavenProject project : session.getProjects()) {
      artifacts.put(project, new LinkedHashSet<Artifact>(project.getArtifacts()));
    }
    try {
      MojoExecutor mojoExecutor = lookup(MojoExecutor.class);
      DependencyContext dependencyContext = mojoExecutor.newDependencyContext(session,
          Collections.singletonList(execution));

      // workaround for http://jira.codehaus.org/browse/MNG-5141
      // use reflection until we can get maven 3.0.4+, which has MNG-5141 fixed
      Method ensureDependenciesAreResolved = mojoExecutor.getClass().getDeclaredMethod("ensureDependenciesAreResolved",
          MojoDescriptor.class, MavenSession.class, DependencyContext.class);
      ensureDependenciesAreResolved.setAccessible(true);
      ensureDependenciesAreResolved.invoke(mojoExecutor, execution.getMojoDescriptor(), session, dependencyContext);

      lookup(BuildPluginManager.class).executeMojo(session, execution);
    } catch(Exception ex) {
      session.getResult().addException(ex);
    } finally {
      for(MavenProject project : session.getProjects()) {
        project.setArtifactFilter(null);
        project.setResolvedArtifacts(null);
        project.setArtifacts(artifacts.get(project));
      }
    }
  }

  public <T> T getConfiguredMojo(MavenSession session, MojoExecution mojoExecution, Class<T> clazz)
      throws CoreException {
    try {
      MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
      // getPluginRealm creates plugin realm and populates pluginDescriptor.classRealm field 
      lookup(BuildPluginManager.class).getPluginRealm(session, mojoDescriptor.getPluginDescriptor());
      return clazz.cast(lookup(MavenPluginManager.class).getConfiguredMojo(Mojo.class, session, mojoExecution));
    } catch(PluginContainerException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          NLS.bind(Messages.MavenImpl_error_mojo, mojoExecution), ex));
    } catch(PluginConfigurationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          NLS.bind(Messages.MavenImpl_error_mojo, mojoExecution), ex));
    } catch(ClassCastException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          NLS.bind(Messages.MavenImpl_error_mojo, mojoExecution), ex));
    } catch(PluginResolutionException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          NLS.bind(Messages.MavenImpl_error_mojo, mojoExecution), ex));
    } catch(PluginManagerException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          NLS.bind(Messages.MavenImpl_error_mojo, mojoExecution), ex));
    }
  }

  public void releaseMojo(Object mojo, MojoExecution mojoExecution) throws CoreException {
    lookup(MavenPluginManager.class).releaseMojo(mojo, mojoExecution);
  }

  public MavenExecutionPlan calculateExecutionPlan(MavenSession session, MavenProject project, List<String> goals,
      boolean setup, IProgressMonitor monitor) throws CoreException {
    try {
      return lookup(LifecycleExecutor.class).calculateExecutionPlan(session, setup, goals.toArray(new String[goals.size()]));
    } catch(Exception ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, NLS.bind(
          Messages.MavenImpl_error_calc_build_plan, ex.getMessage()), ex));
    }
  }
  
  public MojoExecution setupMojoExecution(MavenSession session, MavenProject project, MojoExecution execution) throws CoreException {
    MojoExecution clone = new MojoExecution(execution.getPlugin(), execution.getGoal(), execution.getExecutionId());
    clone.setMojoDescriptor(execution.getMojoDescriptor());
    if(execution.getConfiguration() != null) {
      clone.setConfiguration(new Xpp3Dom(execution.getConfiguration()));
    }
    clone.setLifecyclePhase(execution.getLifecyclePhase());
    LifecycleExecutionPlanCalculator executionPlanCalculator = lookup(LifecycleExecutionPlanCalculator.class);
    try {
      executionPlanCalculator.setupMojoExecution(session, project, clone);
    } catch(Exception ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, NLS.bind(
          Messages.MavenImpl_error_calc_build_plan, ex.getMessage()), ex));
    }
    return clone;
  }

  public ArtifactRepository getLocalRepository() throws CoreException {
    try {
      String localRepositoryPath = getLocalRepositoryPath();
      if(localRepositoryPath != null) {
        return lookup(RepositorySystem.class).createLocalRepository(new File(localRepositoryPath));
      }
      return lookup(RepositorySystem.class).createLocalRepository(RepositorySystem.defaultUserLocalRepository);
    } catch(InvalidRepositoryException ex) {
      // can't happen
      throw new IllegalStateException(ex);
    }
  }

  public Settings getSettings() throws CoreException {
    return getSettings(false);
  }

  public synchronized Settings getSettings(boolean reload) throws CoreException {
    // MUST NOT use createRequest!

    if (reload || settings==null) {
      // TODO: Can't that delegate to buildSettings()?
      SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
      request.setSystemProperties(System.getProperties());
      if(mavenConfiguration.getGlobalSettingsFile() != null) {
        request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
      }
      if(mavenConfiguration.getUserSettingsFile() != null) {
        request.setUserSettingsFile(new File(mavenConfiguration.getUserSettingsFile()));
      }
      try {
        settings = lookup(SettingsBuilder.class).build(request).getEffectiveSettings();
      } catch(SettingsBuildingException ex) {
        String msg = "Could not read settings.xml, assuming default values";
        log.error(msg, ex);
        /*
         * NOTE: This method provides input for various other core functions, just bailing out would make m2e highly
         * unusuable. Instead, we fail gracefully and just ignore the broken settings, using defaults.
         */
        settings = new Settings();
      }
    }
    return settings;
  }

  public Settings buildSettings(String globalSettings, String userSettings) throws CoreException {
    SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
    request.setGlobalSettingsFile(globalSettings != null ? new File(globalSettings) : null);
    request.setUserSettingsFile(userSettings != null ? new File(userSettings) : null);
    try {
      return lookup(SettingsBuilder.class).build(request).getEffectiveSettings();
    } catch(SettingsBuildingException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_read_settings,
          ex));
    }
  }

  public void writeSettings(Settings settings, OutputStream out) throws CoreException {
    try {
      lookup(SettingsWriter.class).write(out, null, settings);
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_write_settings,
          ex));
    }
  }

  public List<SettingsProblem> validateSettings(String settings) {
    List<SettingsProblem> problems = new ArrayList<SettingsProblem>();
    if(settings != null) {
      File settingsFile = new File(settings);
      if(settingsFile.canRead()) {
        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setUserSettingsFile(settingsFile);
        try {
          lookup(SettingsBuilder.class).build(request);
        } catch(SettingsBuildingException ex) {
          problems.addAll(ex.getProblems());
        } catch(CoreException ex) {
          problems.add(new DefaultSettingsProblem(ex.getMessage(), Severity.FATAL, settings, -1, -1, ex));
        }
      } else {
        problems.add(new DefaultSettingsProblem(NLS.bind(Messages.MavenImpl_error_read_settings2, settings),
            SettingsProblem.Severity.ERROR, settings, -1, -1, null));
      }
    }

    return problems;
  }

  public void reloadSettings() throws CoreException {
    Settings settings = getSettings(true);
    for(ISettingsChangeListener listener : settingsListeners) {
      try {
        listener.settingsChanged(settings);
      } catch(CoreException e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  public Server decryptPassword(Server server) throws CoreException {
    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(server);
    SettingsDecryptionResult result = lookup(SettingsDecrypter.class).decrypt(request);
    for(SettingsProblem problem : result.getProblems()) {
      log.warn(problem.getMessage(), problem.getException());
    }
    return result.getServer();
  }

  public void mavenConfigurationChange(MavenConfigurationChangeEvent event) throws CoreException {
    if(MavenConfigurationChangeEvent.P_USER_SETTINGS_FILE.equals(event.getKey())
        || MavenPreferenceConstants.P_GLOBAL_SETTINGS_FILE.equals(event.getKey())) {
      reloadSettings();
    }
  }

  public Model readModel(InputStream in) throws CoreException {
    try {
      return lookup(ModelReader.class).read(in, null);
    } catch(IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_read_pom, e));
    }
  }

  public Model readModel(File pomFile) throws CoreException {
    try {
      BufferedInputStream is = new BufferedInputStream(new FileInputStream(pomFile));
      try {
        return readModel(is);
      } finally {
        IOUtil.close(is);
      }
    } catch(IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_read_pom, e));
    }
  }

  public void writeModel(Model model, OutputStream out) throws CoreException {
    try {
      lookup(ModelWriter.class).write(out, null, model);
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_write_pom, ex));
    }
  }

  public MavenProject readProject(File pomFile, IProgressMonitor monitor) throws CoreException {
    try {
      MavenExecutionRequest request = createExecutionRequest(monitor);
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
      ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
      configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      configuration.setRepositorySession(createRepositorySession(request));
      return lookup(ProjectBuilder.class).build(pomFile, configuration).getProject();
    } catch(ProjectBuildingException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_read_project,
          ex));
    } catch(MavenExecutionRequestPopulationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_read_project,
          ex));
    }
  }

  public MavenExecutionResult readProject(MavenExecutionRequest request, IProgressMonitor monitor) throws CoreException {
    long start = System.currentTimeMillis();

    File pomFile = request.getPom();
    log.debug("Reading Maven project: {}", pomFile.getAbsoluteFile()); //$NON-NLS-1$
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    try {
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
      ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
      configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      configuration.setRepositorySession(createRepositorySession(request));
      ProjectBuildingResult projectBuildingResult = lookup(ProjectBuilder.class).build(pomFile, configuration);
      result.setProject(projectBuildingResult.getProject());
      result.setDependencyResolutionResult(projectBuildingResult.getDependencyResolutionResult());
    } catch(ProjectBuildingException ex) {
      return result.addException(ex);
    } catch(MavenExecutionRequestPopulationException ex) {
      return result.addException(ex);
    } finally {
      log.debug("Read Maven project: {} in {} ms", pomFile.getAbsoluteFile(), System.currentTimeMillis() - start); //$NON-NLS-1$
    }
    return result;
  }

  /**
   * Makes MavenProject instances returned by #readProject methods suitable for caching and reuse with other
   * MavenSession instances.<br/>
   * Do note that MavenProject.getParentProject() cannot be used for detached MavenProject instances. Use
   * #resolveParentProject to resolve parent project instance.
   */
  public void detachFromSession(MavenProject project) throws CoreException {
    project.getProjectBuildingRequest().setRepositorySession(lookup(ContextRepositorySystemSession.class));
  }

  public MavenProject resolveParentProject(MavenExecutionRequest request, MavenProject child, IProgressMonitor monitor)
      throws CoreException {
    ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
    configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    configuration.setRepositorySession(createRepositorySession(request));

    try {
      configuration.setRemoteRepositories(child.getRemoteArtifactRepositories());

      File parentFile = child.getParentFile();
      if(parentFile != null) {
        return lookup(ProjectBuilder.class).build(parentFile, configuration).getProject();
      }

      Artifact parentArtifact = child.getParentArtifact();
      if(parentArtifact != null) {
        return lookup(ProjectBuilder.class).build(parentArtifact, configuration).getProject();
      }
    } catch(ProjectBuildingException ex) {
      log.error("Could not read parent project", ex);
    }

    return null;
  }

  public Artifact resolve(String groupId, String artifactId, String version, String type, String classifier,
      List<ArtifactRepository> remoteRepositories, IProgressMonitor monitor) throws CoreException {
    Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version, type,
        classifier);

    if(remoteRepositories == null) {
      try {
        remoteRepositories = getArtifactRepositories();
      } catch(CoreException e) {
        // we've tried
        remoteRepositories = Collections.emptyList();
      }
    }

    ArtifactRepository localRepository = getLocalRepository();

    org.sonatype.aether.RepositorySystem repoSystem = lookup(org.sonatype.aether.RepositorySystem.class);

    MavenRepositorySystemSession session = new MavenRepositorySystemSession();
    session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(new LocalRepository(localRepository
        .getBasedir())));
    session.setTransferListener(createArtifactTransferListener(monitor));

    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(RepositoryUtils.toArtifact(artifact));
    request.setRepositories(RepositoryUtils.toRepos(remoteRepositories));

    ArtifactResult result;
    try {
      result = repoSystem.resolveArtifact(session, request);
    } catch(ArtifactResolutionException ex) {
      result = ex.getResults().get(0);
    }

    setLastUpdated(localRepository, remoteRepositories, artifact);

    if(result.isResolved()) {
      artifact.selectVersion(result.getArtifact().getVersion());
      artifact.setFile(result.getArtifact().getFile());
      artifact.setResolved(true);
    } else {
      ArrayList<IStatus> members = new ArrayList<IStatus>();
      for(Exception e : result.getExceptions()) {
        if(!(e instanceof ArtifactNotFoundException)) {
          members.add(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, e.getMessage(), e));
        }
      }
      if(members.isEmpty()) {
        members.add(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, NLS.bind(Messages.MavenImpl_error_missing, artifact), null));
      }
      IStatus[] newMembers = members.toArray(new IStatus[members.size()]);
      throw new CoreException(new MultiStatus(IMavenConstants.PLUGIN_ID, -1, newMembers, NLS.bind(
          Messages.MavenImpl_error_resolve, artifact.toString()),
          null));
    }

    return artifact;
  }

  public String getArtifactPath(ArtifactRepository repository, String groupId, String artifactId, String version,
      String type, String classifier) throws CoreException {
    Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version, type,
        classifier);
    return repository.pathOf(artifact);
  }

  private void setLastUpdated(ArtifactRepository localRepository, List<ArtifactRepository> remoteRepositories,
      Artifact artifact) throws CoreException {

    Properties lastUpdated = loadLastUpdated(localRepository, artifact);

    String timestamp = Long.toString(System.currentTimeMillis());

    for(ArtifactRepository repository : remoteRepositories) {
      lastUpdated.setProperty(getLastUpdatedKey(repository, artifact), timestamp);
    }

    File lastUpdatedFile = getLastUpdatedFile(localRepository, artifact);
    try {
      lastUpdatedFile.getParentFile().mkdirs();
      BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(lastUpdatedFile));
      try {
        lastUpdated.store(os, null);
      } finally {
        IOUtil.close(os);
      }
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_write_lastUpdated, ex));
    }
  }

  /**
   * This is a temporary implementation that only works for artifacts resolved using #resolve.
   */
  public boolean isUnavailable(String groupId, String artifactId, String version, String type, String classifier,
      List<ArtifactRepository> remoteRepositories) throws CoreException {
    Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version, type,
        classifier);

    ArtifactRepository localRepository = getLocalRepository();

    File artifactFile = new File(localRepository.getBasedir(), localRepository.pathOf(artifact));

    if(artifactFile.canRead()) {
      // artifact is available locally
      return false;
    }

    if(remoteRepositories == null || remoteRepositories.isEmpty()) {
      // no remote repositories
      return true;
    }

    // now is the hard part
    Properties lastUpdated = loadLastUpdated(localRepository, artifact);

    for(ArtifactRepository repository : remoteRepositories) {
      String timestamp = lastUpdated.getProperty(getLastUpdatedKey(repository, artifact));
      if(timestamp == null) {
        // availability of the artifact from this repository has not been checked yet 
        return false;
      }
    }

    // artifact is not available locally and all remote repositories have been checked in the past
    return true;
  }

  private String getLastUpdatedKey(ArtifactRepository repository, Artifact artifact) {
    StringBuilder key = new StringBuilder();

    // repository part
    key.append(repository.getId());
    if(repository.getAuthentication() != null) {
      key.append('|').append(repository.getAuthentication().getUsername());
    }
    key.append('|').append(repository.getUrl());

    // artifact part
    key.append('|').append(artifact.getClassifier());

    return key.toString();
  }

  private Properties loadLastUpdated(ArtifactRepository localRepository, Artifact artifact) throws CoreException {
    Properties lastUpdated = new Properties();
    File lastUpdatedFile = getLastUpdatedFile(localRepository, artifact);
    try {
      BufferedInputStream is = new BufferedInputStream(new FileInputStream(lastUpdatedFile));
      try {
        lastUpdated.load(is);
      } finally {
        IOUtil.close(is);
      }
    } catch(FileNotFoundException ex) {
      // that's okay
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_read_lastUpdated, ex));
    }
    return lastUpdated;
  }

  private File getLastUpdatedFile(ArtifactRepository localRepository, Artifact artifact) {
    return new File(localRepository.getBasedir(), basePathOf(localRepository, artifact) + "/" //$NON-NLS-1$
        + "m2e-lastUpdated.properties"); //$NON-NLS-1$
  }

  private static final char PATH_SEPARATOR = '/';

  private static final char GROUP_SEPARATOR = '.';

  private String basePathOf(ArtifactRepository repository, Artifact artifact) {
    StringBuilder path = new StringBuilder(128);

    path.append(formatAsDirectory(artifact.getGroupId())).append(PATH_SEPARATOR);
    path.append(artifact.getArtifactId()).append(PATH_SEPARATOR);
    path.append(artifact.getBaseVersion()).append(PATH_SEPARATOR);

    return path.toString();
  }

  private String formatAsDirectory(String directory) {
    return directory.replace(GROUP_SEPARATOR, PATH_SEPARATOR);
  }

  public <T> T getMojoParameterValue(MavenSession session, MojoExecution mojoExecution, String parameter,
      Class<T> asType) throws CoreException {
    try {
      MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

      ClassRealm pluginRealm = lookup(BuildPluginManager.class).getPluginRealm(session,
          mojoDescriptor.getPluginDescriptor());

      ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);

      ConfigurationConverter typeConverter = converterLookup.lookupConverterForType(asType);

      Xpp3Dom dom = mojoExecution.getConfiguration();

      if(dom == null) {
        return null;
      }

      PlexusConfiguration pomConfiguration = new XmlPlexusConfiguration(dom);

      PlexusConfiguration configuration = pomConfiguration.getChild(parameter);

      if(configuration == null) {
        return null;
      }

      Object value = typeConverter.fromConfiguration(converterLookup, configuration, asType,
          mojoDescriptor.getImplementationClass(), pluginRealm, expressionEvaluator, null);
      return asType.cast(value);
    } catch(Exception e) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, NLS.bind(
          Messages.MavenImpl_error_param_for_execution, parameter, mojoExecution.getExecutionId()), e));
    }
  }

  public <T> T getMojoParameterValue(String parameter, Class<T> type, MavenSession session, Plugin plugin,
      ConfigurationContainer configuration, String goal) throws CoreException {
    Xpp3Dom config = (Xpp3Dom) configuration.getConfiguration();
    config = (config != null) ? config.getChild(parameter) : null;

    PlexusConfiguration paramConfig = null;

    if(config == null) {
      MojoDescriptor mojoDescriptor;

      try {
        mojoDescriptor = lookup(BuildPluginManager.class).getMojoDescriptor(plugin, goal,
            session.getCurrentProject().getRemotePluginRepositories(), session.getRepositorySession());
      } catch(PluginNotFoundException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            Messages.MavenImpl_error_param, ex));
      } catch(PluginResolutionException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            Messages.MavenImpl_error_param, ex));
      } catch(PluginDescriptorParsingException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            Messages.MavenImpl_error_param, ex));
      } catch(MojoNotFoundException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            Messages.MavenImpl_error_param, ex));
      } catch(InvalidPluginDescriptorException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            Messages.MavenImpl_error_param, ex));
      }

      PlexusConfiguration defaultConfig = mojoDescriptor.getMojoConfiguration();
      if(defaultConfig != null) {
        paramConfig = defaultConfig.getChild(parameter, false);
      }
    } else {
      paramConfig = new XmlPlexusConfiguration(config);
    }

    if(paramConfig == null) {
      return null;
    }

    try {
      MojoExecution mojoExecution = new MojoExecution(plugin, goal, "default"); //$NON-NLS-1$

      ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);

      ConfigurationConverter typeConverter = converterLookup.lookupConverterForType(type);

      Object value = typeConverter.fromConfiguration(converterLookup, paramConfig, type, Object.class,
          plexus.getContainerRealm(), expressionEvaluator, null);
      return type.cast(value);
    } catch(ComponentConfigurationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_param, ex));
    } catch(ClassCastException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_param, ex));
    }
  }

  /**
   * Temporary solution/workaround for http://jira.codehaus.org/browse/MNG-4194. Extensions realm is created each time
   * MavenProject instance is built, so we have to remove unused extensions realms to avoid OOME.
   */
  public void releaseExtensionsRealm(MavenProject project) {
    ClassRealm realm = project.getClassRealm();
    if(realm != null && realm != plexus.getContainerRealm()) {
      ClassWorld world = ((MutablePlexusContainer) plexus).getClassWorld();
      try {
        world.disposeRealm(realm.getId());
      } catch(NoSuchRealmException ex) {
        log.error("Could not dispose of project extensions class realm", ex);
      }
    }
  }

  public ArtifactRepository createArtifactRepository(String id, String url) throws CoreException {
    Repository repository = new Repository();
    repository.setId(id);
    repository.setUrl(url);
    repository.setLayout("default"); //$NON-NLS-1$

    ArtifactRepository repo;
    try {
      repo = lookup(RepositorySystem.class).buildArtifactRepository(repository);
      ArrayList<ArtifactRepository> repos = new ArrayList<ArtifactRepository>(Arrays.asList(repo));
      injectSettings(repos);
    } catch(InvalidRepositoryException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_create_repo, ex));
    }
    return repo;
  }

  public List<ArtifactRepository> getArtifactRepositories() throws CoreException {
    return getArtifactRepositories(true);
  }

  public List<ArtifactRepository> getArtifactRepositories(boolean injectSettings) throws CoreException {
    ArrayList<ArtifactRepository> repositories = new ArrayList<ArtifactRepository>();
    for(Profile profile : getActiveProfiles()) {
      addArtifactRepositories(repositories, profile.getRepositories());
    }

    addDefaultRepository(repositories);

    if(injectSettings) {
      injectSettings(repositories);
    }

    return removeDuplicateRepositories(repositories);
  }

  private List<ArtifactRepository> removeDuplicateRepositories(ArrayList<ArtifactRepository> repositories) {
    ArrayList<ArtifactRepository> result = new ArrayList<ArtifactRepository>();

    HashSet<String> keys = new HashSet<String>();
    for(ArtifactRepository repository : repositories) {
      StringBuilder key = new StringBuilder();
      if(repository.getId() != null) {
        key.append(repository.getId());
      }
      key.append(':').append(repository.getUrl()).append(':');
      if(repository.getAuthentication() != null && repository.getAuthentication().getUsername() != null) {
        key.append(repository.getAuthentication().getUsername());
      }
      if(keys.add(key.toString())) {
        result.add(repository);
      }
    }
    return result;
  }

  private void injectSettings(ArrayList<ArtifactRepository> repositories) throws CoreException {
    Settings settings = getSettings();

    lookup(RepositorySystem.class).injectMirror(repositories, getMirrors());
    lookup(RepositorySystem.class).injectProxy(repositories, settings.getProxies());
    lookup(RepositorySystem.class).injectAuthentication(repositories, settings.getServers());
  }

  private void addDefaultRepository(ArrayList<ArtifactRepository> repositories) throws CoreException {
    for(ArtifactRepository repository : repositories) {
      if(RepositorySystem.DEFAULT_REMOTE_REPO_ID.equals(repository.getId())) {
        return;
      }
    }
    try {
      repositories.add(0, lookup(RepositorySystem.class).createDefaultRemoteRepository());
    } catch(InvalidRepositoryException ex) {
      log.error("Unexpected exception", ex);
    }
  }

  private void addArtifactRepositories(ArrayList<ArtifactRepository> artifactRepositories, List<Repository> repositories)
      throws CoreException {
    for(Repository repository : repositories) {
      try {
        ArtifactRepository artifactRepository = lookup(RepositorySystem.class).buildArtifactRepository(repository);
        artifactRepositories.add(artifactRepository);
      } catch(InvalidRepositoryException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, Messages.MavenImpl_error_read_settings,
            ex));
      }
    }
  }

  private List<Profile> getActiveProfiles() throws CoreException {
    Settings settings = getSettings();
    List<String> activeProfilesIds = settings.getActiveProfiles();
    ArrayList<Profile> activeProfiles = new ArrayList<Profile>();
    for(org.apache.maven.settings.Profile settingsProfile : settings.getProfiles()) {
      if((settingsProfile.getActivation() != null && settingsProfile.getActivation().isActiveByDefault())
          || activeProfilesIds.contains(settingsProfile.getId())) {
        Profile profile = SettingsUtils.convertFromSettingsProfile(settingsProfile);
        activeProfiles.add(profile);
      }
    }
    return activeProfiles;
  }

  public List<ArtifactRepository> getPluginArtifactRepositories() throws CoreException {
    return getPluginArtifactRepositories(true);
  }

  public List<ArtifactRepository> getPluginArtifactRepositories(boolean injectSettings) throws CoreException {
    ArrayList<ArtifactRepository> repositories = new ArrayList<ArtifactRepository>();
    for(Profile profile : getActiveProfiles()) {
      addArtifactRepositories(repositories, profile.getPluginRepositories());
    }
    addDefaultRepository(repositories);

    if(injectSettings) {
      injectSettings(repositories);
    }

    return removeDuplicateRepositories(repositories);
  }

  public Mirror getMirror(ArtifactRepository repo) throws CoreException {
    MavenExecutionRequest request = createExecutionRequest(new NullProgressMonitor());
    populateDefaults(request);
    return lookup(RepositorySystem.class).getMirror(repo, request.getMirrors());
  };

  public void populateDefaults(MavenExecutionRequest request) throws CoreException {
    try {
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
    } catch(MavenExecutionRequestPopulationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_read_config, ex));
    }
  }

  public List<Mirror> getMirrors() throws CoreException {
    MavenExecutionRequest request = createExecutionRequest(null);
    populateDefaults(request);
    return request.getMirrors();
  }

  public void addSettingsChangeListener(ISettingsChangeListener listener) {
    settingsListeners.add(listener);
  }

  public void removeSettingsChangeListener(ISettingsChangeListener listener) {
    settingsListeners.remove(listener);
  }

  public void addLocalRepositoryListener(ILocalRepositoryListener listener) {
    localRepositoryListeners.add(listener);
  }

  public void removeLocalRepositoryListener(ILocalRepositoryListener listener) {
    localRepositoryListeners.remove(listener);
  }

  public List<ILocalRepositoryListener> getLocalRepositoryListeners() {
    return localRepositoryListeners;
  }

  @SuppressWarnings("deprecation")
  public WagonTransferListenerAdapter createTransferListener(IProgressMonitor monitor) {
    return new WagonTransferListenerAdapter(this, monitor);
  }

  public TransferListener createArtifactTransferListener(IProgressMonitor monitor) {
    return new ArtifactTransferListenerAdapter(this, monitor);
  }

  public synchronized PlexusContainer getPlexusContainer() throws CoreException {
    if(plexus == null) {
      try {
        plexus = newPlexusContainer();
        plexus.setLoggerManager(new EclipseLoggerManager(mavenConfiguration));
      } catch(PlexusContainerException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            Messages.MavenImpl_error_init_maven, ex));
      }
    }
    return plexus;
  }

  public ProxyInfo getProxyInfo(String protocol) throws CoreException {
    Settings settings = getSettings();

    for(Proxy proxy : settings.getProxies()) {
      if(proxy.isActive() && protocol.equalsIgnoreCase(proxy.getProtocol())) {
        ProxyInfo proxyInfo = new ProxyInfo();
        proxyInfo.setType(proxy.getProtocol());
        proxyInfo.setHost(proxy.getHost());
        proxyInfo.setPort(proxy.getPort());
        proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());
        proxyInfo.setUserName(proxy.getUsername());
        proxyInfo.setPassword(proxy.getPassword());
        return proxyInfo;
      }
    }

    return null;
  }

  public List<MavenProject> getSortedProjects(List<MavenProject> projects) throws CoreException {
    try {
      ProjectSorter rm = new ProjectSorter(projects);
      return rm.getSortedProjects();
    } catch(CycleDetectedException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, Messages.MavenImpl_error_sort, ex));
    } catch(DuplicateProjectException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, Messages.MavenImpl_error_sort, ex));
    }
  }

  public String resolvePluginVersion(String groupId, String artifactId, MavenSession session) throws CoreException {
    Plugin plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    PluginVersionRequest request = new DefaultPluginVersionRequest(plugin, session);
    try {
      return lookup(PluginVersionResolver.class).resolve(request).getVersion();
    } catch(PluginVersionResolutionException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, ex.getMessage(), ex));
    }
  }

  private <T> T lookup(Class<T> clazz) throws CoreException {
    try {
      return getPlexusContainer().lookup(clazz);
    } catch(ComponentLookupException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          Messages.MavenImpl_error_lookup, ex));
    }
  }

  private static DefaultPlexusContainer newPlexusContainer() throws PlexusContainerException {
    ContainerConfiguration mavenCoreCC = new DefaultContainerConfiguration().setClassWorld(
        new ClassWorld(MAVEN_CORE_REALM_ID, ClassWorld.class.getClassLoader())).setName(
        "mavenCore"); //$NON-NLS-1$

    mavenCoreCC.setAutoWiring(true);

    return new DefaultPlexusContainer(mavenCoreCC, new ExtensionModule());
  }

  public synchronized void disposeContainer() {
    if(plexus != null) {
      plexus.dispose();
    }
  }

  public ClassLoader getProjectRealm(MavenProject project) {
    ClassLoader classLoader = project.getClassRealm();
    if(classLoader == null) {
      classLoader = plexus.getContainerRealm();
    }
    return classLoader;
  }

  public void interpolateModel(MavenProject project, Model model)
      throws CoreException {
    ModelBuildingRequest request = new DefaultModelBuildingRequest();
    request.setUserProperties(project.getProperties());
    ModelProblemCollector problems = new ModelProblemCollector() {
      public void add(ModelProblem.Severity severity, String message, InputLocation location, Exception cause) {
      }
    };
    lookup(ModelInterpolator.class).interpolateModel(model, project.getBasedir(), request, problems);
  }
}
