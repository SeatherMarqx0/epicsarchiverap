/*******************************************************************************
 * Copyright (c) 2011 The Board of Trustees of the Leland Stanford Junior University
 * as Operator of the SLAC National Accelerator Laboratory.
 * Copyright (c) 2011 Brookhaven National Laboratory.
 * EPICS archiver appliance is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 *******************************************************************************/
package org.epics.archiverappliance.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;

import org.epics.archiverappliance.config.exception.AlreadyRegisteredException;
import org.epics.archiverappliance.config.exception.ConfigException;
import org.epics.archiverappliance.engine.pv.EngineContext;
import org.epics.archiverappliance.etl.common.PBThreeTierETLPVLookup;
import org.epics.archiverappliance.mgmt.MgmtRuntimeState;
import org.epics.archiverappliance.mgmt.policy.PolicyConfig;
import org.epics.archiverappliance.retrieval.RetrievalState;

import com.google.common.eventbus.EventBus;

/**
 * Interface for appliance configuration.
 * One gets to a config service implementation thru dependency injection of one kind or the other.
 * In a servlet container, this is initialized by ArchServletContextListener (which is registered as part of the web.xml).
 * ArchServletContextListener is also used in the unit tests that involve tomcat. 
 * Guice is a good option for this but it takes over the dispatch logic from tomcat and we'll need to investigate if that has any impact. 
 * @author mshankar
 */
public interface ConfigService {
	/**
	 * This is the environment variable that points to the file containing the various appliances in this cluster.
	 * This list of appliances is expected to be the same for all appliances in the cluster; so it is perfectly legal to place it in NFS somewhere and point to the same file/location from all appliances in the cluster.
	 * It is reasonably important that all appliances see the same list of cluster members or we tend to have split-brain effects (<a href="http://en.wikipedia.org/wiki/Split-brain_%28computing%29">See wikipedia</a>).
	 * The format of the file itself is simple XML like so
	 * <code><pre>
	 * &lt;appliances&gt;
	 *   &lt;appliance&gt;
	 *     &lt;identity&gt;archiver&lt;/identity&gt;
	 *     &lt;cluster_inetport&gt;archiver:77770&lt;/cluster_inetport&gt;
	 *     &lt;mgmt_url&gt;http://archiver.slac.stanford.edu:77765/mgmt/bpl&lt;/mgmt_url&gt;
	 *     &lt;engine_url&gt;http://archiver.slac.stanford.edu:77765/engine/bpl&lt;/engine_url&gt;
	 *     &lt;etl_url&gt;http://archiver.slac.stanford.edu:77765/etl/bpl&lt;/etl_url&gt;
	 *     &lt;retrieval_url&gt;http://archiver.slac.stanford.edu:77765/retrieval/bpl&lt;/retrieval_url&gt;
	 *     &lt;data_retrieval_url&gt;http://archiver.slac.stanford.edu:77765/retrieval/&lt;/data_retrieval_url&gt;
	 *   &lt;/appliance&gt;
	 * &lt;/appliances&gt;
	 * </pre></code>
	 * Note that the appliance identity as defined by the <code>ARCHAPPL_MYIDENTITY</code> has to match the <code>identity</code> element of one of the appliances in the list of appliances as defined by the <code>ARCHAPPL_APPLIANCES</code>.
	 * Each appliance (which includes the mgmt, engine, etl and retrieval WAR's) must have a unique identity. 
	 * <br/>
	 * If the <code>ARCHAPPL_APPLIANCES</code> is not set, then we look for a file called <code>appliances.xml</code> in the WEB-INF/classes of the current WAR using WEB-INF/classes/appliances.xml.
	 * The default build script places the site-specific <code>appliances.xml</code> into WEB-INF/classes/appliances.xml.
	 */
	public static final String ARCHAPPL_APPLIANCES = "ARCHAPPL_APPLIANCES";
	
	/**
	 * This is an optional environment variable that determines this appliance's identity.
	 * If this is not set, the archiver appliance uses <code>InetAddress.getLocalHost().getCanonicalHostName()</code> to determine the FQDN of this machine. 
	 * This is then used as the appliance identity to lookup the appliance info in <code>ARCHAPPL_APPLIANCES</code>. 
	 * To use this environment variable, for example, in Linux, set the appliance's identity using <code>export ARCHAPPL_MYIDENTITY="archiver"</code>.
	 * Each appliance (which includes the mgmt, engine, etl and retrieval WAR's) must have a unique identity. 
	 * 
	 * To accommodate the multi-instance unit tests, if this environment variable is not set, we check for the existence of the java system property <code>ARCHAPPL_MYIDENTITY</code>.
	 * Typically, the multi-instance unit tests (which are incapable of altering the environment) use the java system property method.
	 * In environments that run the unit tests, leave the environment variable ARCHAPPL_MYIDENTITY unset so that the various multi-instance unit tests have the ability to control the appliance identity.
	 */
	public static final String ARCHAPPL_MYIDENTITY = "ARCHAPPL_MYIDENTITY";
	
	/**
	 * This is the environment variable that identifies the site (LCLS, LCLSII, slacdev, NSLSII etc) to be used when generating the war files.
	 * This is primarily a build-time property; the build.xml has various site specific hooks which let you change the appliances.xml, policies, images etc on a per site basis.
	 * The unit tests use the <code>tests</code> site which is also the default site if this environment variable is not specified.
	 * Files for a site are stored in the sitespecific/<site> folder. 
	 */
	public static final String ARCHAPPL_SITEID = "ARCHAPPL_SITEID";
	
	
	/**
	 * This is an optional environment variable/system property that is used to identify the config service implementation.
	 *  
	 */
	public static final String ARCHAPPL_CONFIGSERVICE_IMPL = "ARCHAPPL_CONFIGSERVICE_IMPL";

	/**
	 * This is the name used to identify the config service implementation in one of the existing singletons (like the ServletContext)
	 */
	public static final String CONFIG_SERVICE_NAME = "org.epics.archiverappliance.config.ConfigService";
	
	
	/**
	 * This is an optional environment/system.property that is used to identity the persistence layer
	 * If this is not set, we initialize MySQLPersistence as the persistence layer; so in production environments, you can leave this unset/blank
	 * Set this to the class name of the class implementing {@link ConfigPersistence ConfigPersistence}
	 * The unit tests however will set this to use InMemoryPersistence, which is a dummy persistence layer.
	 */
	public static final String ARCHAPPL_PERSISTENCE_LAYER = "ARCHAPPL_PERSISTENCE_LAYER";
	
	/**
	 * This is an optional environment/system.property that is used to specify the location of the <code>policies.py</code> policies file.
	 * If this is not set, we search for the  <code>policies.py</code> in the servlet classpath using the path <code>/WEB-INF/classes/policies.py</code> 
	 */
	public static final String ARCHAPPL_POLICIES = "ARCHAPPL_POLICIES";


	/**
	 * If you have a null constructor and need a ServletContext, implement this method.
	 * @param sce
	 */
	public void initialize(ServletContext sce) throws ConfigException;
	
	
	/**
	 * We expect to live within a servlet container. 
	 * This call returns the full path to the WEB-INF folder of the webapp as it is deployed in the container.
	 * Is typically a call to the <code>servletContext.getRealPath("WEB-INF/")</code> 
	 * @return
	 */
	public String getWebInfFolder(); 
	
	/**
	 * This method is called after the mgmt WAR file has started up and set up the cluster and recovered data from persistence.
	 * Each appliance's mgmt war is responsible for calling this method on the other components (engine, etl and retrieval) using BPL.
	 * Until this method is called on all the web apps, the cluster is not considered to have started up.
	 * @throws ConfigException
	 */
	public void postStartup() throws ConfigException;
	
	public enum STARTUP_SEQUENCE { ZEROTH_STATE, READY_TO_JOIN_APPLIANCE, POST_STARTUP_RUNNING, STARTUP_COMPLETE}; 

	/**
	 * Used for inter-appliance startup checks.
	 * @return
	 */
	public STARTUP_SEQUENCE getStartupState();
	
	public enum WAR_FILE { MGMT, RETRIEVAL, ETL, ENGINE }
	
	/**
	 * Have we completed all the startup steps?
	 */
	public boolean isStartupComplete();
	
	/**
	 * The name/path of the archappl.properties file.
	 * By default, we look for archappl.properties in the webapp's classpath - this will typically resolve into WEB-INF/classes of the webapp.
	 * However, you can override this using an environment variable (or java system property) of the same name.
	 * For example, <code>export ARCHAPPL_PROPERTIES_FILENAME=/etc/mylab_archappl.properties</code> should force the components to load their properties from <code>/etc/mylab_archappl.properties</code>
	 */
	static final String ARCHAPPL_PROPERTIES_FILENAME = "ARCHAPPL_PROPERTIES_FILENAME";
	
	/**
	 * This is the name of the properties file that is looked for in the webapp's classpath if one is not specified using a environment/JVM property.
	 */
	static final String DEFAULT_ARCHAPPL_PROPERTIES_FILENAME = "archappl.properties";
	/**
	 * An arbitrary list of name/value pairs can be specified in a file called archappl.properties that is loaded from the classpath. 
	 * @return
	 */
	public Properties getInstallationProperties();
	
	/**
	 * Get all the appliances in this cluster.
	 * Much goodness is facilitated if the objects are returned in the same order (perhaps order of creation) all the time.
	 * @return
	 */
	public Iterable<ApplianceInfo> getAppliancesInCluster();
	
	
	/**
	 * Get the appliance information for this appliance.
	 * @return
	 */
	public ApplianceInfo getMyApplianceInfo();
	
	/**
	 * Given an identity of an appliance, return the appliance info for that appliance
	 * @param identity
	 * @return
	 */
	public ApplianceInfo getAppliance(String identity);
	
	/**
	 * Get an exhaustive list of all the PVs this cluster of appliances knows about
	 * Much goodness is facilitated if the objects are returned in the same order (perhaps order of creation) all the time.
	 * @return
	 */
	public Iterable<String> getAllPVs();
	
	/**
	 * Given a PV, get us the appliance that is responsible for archiving it.
	 * Note that this may be null as the assignment of PV's to appliances can take some time. 
	 * @param pvName
	 * @return
	 */
	public ApplianceInfo getApplianceForPV(String pvName);
	
	/**
	 * Get all PVs being archived by this appliance.
	 * Much goodness is facilitated if the objects are returned in the same order (perhaps order of creation) all the time.
	 * @param info
	 * @return
	 */
	public Iterable<String> getPVsForAppliance(ApplianceInfo info); 
	
	
	/**
	 * Get all the PVs for this appliance.
	 * Much goodness is facilitated if the objects are returned in the same order (perhaps order of creation) all the time.
	 * @return
	 */
	public Iterable<String> getPVsForThisAppliance();
	
	/**
	 * Make changes in the config service to register this PV to an appliance
	 * @param pvName
	 * @param applianceInfo
	 */
	public void registerPVToAppliance(String pvName, ApplianceInfo applianceInfo) throws AlreadyRegisteredException;

	
	/**
	 * Gets information about a PV's type, i.e its DBR type, graphic limits etc.
	 * This information is assumed to be somewhat static and is expected to come from a cache if possible as it is used in data retrieval.
	 * @param pvName
	 * @return
	 */
	public PVTypeInfo getTypeInfoForPV(String pvName);
	
	/**
	 * Update the type information about a PV; updating both ther persistent and cached versions of the information. 
	 * Clients are not expected to call this method a million times a second. 
	 * In general, this is expected to be called when archiving a PV for the first time, or perhaps when an appserver startups etc...
	 * @param pvName
	 * @param typeInfo
	 */
	public void updateTypeInfoForPV(String pvName, PVTypeInfo typeInfo);
	
	
	/**
	 * Remove the pv from all cached and persisted configuration.
	 * @param pvName
	 */
	public void removePVFromCluster(String pvName);

	
	/**
	 * Facilitates various optimizations for BPL that uses appliance wide information by caching and maintaining this information on a per appliance basis
	 * 
	 * @return
	 */
	public ApplianceAggregateInfo getAggregatedApplianceInfo(ApplianceInfo applianceInfo) throws IOException;
	
	/**
	 * The workflow for requesting a PV to be archived consists of multiple steps
	 * This method adds a PV to the persisted list of PVs that are currently engaged in this workflow in addition to any user specified overrides
	 * @param pvName
	 * @param userSpecifiedSamplingParams - Use a null contructor for userSpecifiedSamplingParams if no override specified.
	 */
	public void addToArchiveRequests(String pvName, UserSpecifiedSamplingParams userSpecifiedSamplingParams);
	
	/**
	 * Update the archive request (mostly with aliases) if and only if we have this in our persistence.
	 * @param pvName
	 * @param userSpecifiedSamplingParams
	 */
	public void updateArchiveRequest(String pvName, UserSpecifiedSamplingParams userSpecifiedSamplingParams);
	
	/**
	 * Gets a list of PVs that are currently engaged in the archive PV workflow
	 * @return
	 */
	public Set<String> getArchiveRequestsCurrentlyInWorkflow();
	
	
	/**
	 * Is this pv in the archive request workflow. 
	 * @param pvname
	 * @return
	 */
	public boolean doesPVHaveArchiveRequestInWorkflow(String pvname);
	
	
	/**
	 * In clustered environments, to give capacity planning a chance to work correctly, we want to kick off the archive PV workflow only after all the machines have started.
	 * This is an approximation for that metric; though not a very satisfactory approximation.
	 * TODO -- Think thru implications of making the appliances.xml strict... 
	 * @return - Initial delay in seconds.
	 */
	public int getInitialDelayBeforeStartingArchiveRequestWorkflow();
	
	/**
	 * Returns any user specified parameters for the archive request. 
	 * @param pvName
	 * @return
	 */
	public UserSpecifiedSamplingParams getUserSpecifiedSamplingParams(String pvName);
	
	/**
	 * Mark this pv as having it archive pv request completed and pull this request out of persistent store
	 * Can be used in the case of aborting a PV archive request as well
	 * @param pvName
	 */
	public void archiveRequestWorkflowCompleted(String pvName);
	
	
	/**
	 * Get a list of extra fields that are obtained when we initially make a request for archiving.
	 * These are used in the policies to make decisions on how to archive the PV.
	 * @return
	 */
	public String[] getExtraFields();
	
	
	/**
	 * Get a list of fields for PVs that are monitored and maintained in the engine.
	 * These are used when displaying the PV in visualization tools like the ArchiveViewer as additional information for the PV.
	 * Some of these could be archived along with the PV but need not be. 
	 * In this case, the engine simply maintains the latest copy in memory and this is served up when data from the engine in included in the stream.
	 * @return
	 */
	public Set<String> getRuntimeFields();

	
	/**
	 * Register an alias
	 * @param aliasName
	 * @param realName - This is the name under which the PV will be archived under
	 */
	public void addAlias(String aliasName, String realName);
	
	
	/**
	 * Remove an alias for the specified realname
	 * @param aliasName
	 * @param realName - This is the name under which the PV will be archived under
	 */
	public void removeAlias(String aliasName, String realName);
	
	
	/**
	 * Get all the aliases in the system. This is used for matching during glob requests in the UI.
	 * @return
	 */
	public List<String> getAllAliases();

	/**
	 * Gets the .NAME field for a PV if it exists. Otherwise, this returns null
	 * @param aliasName
	 * @return
	 */
	public String getRealNameForAlias(String aliasName);
	
	/**
	 * Return the text of the policy for this installation.
	 * Gets you an InputStream; remember to close it.
	 * @return
	 */
	public InputStream getPolicyText() throws IOException;

	/**
	 * Given a pvName (for now, we should have a pv details object of some kind soon), determine the policy applicable for archiving this PV.
	 * @param pvName
	 * @param metaInfo
	 * @param userSpecParams
	 * @return
	 * @throws IOException
	 */
	public PolicyConfig computePolicyForPV(String pvName, MetaInfo metaInfo, UserSpecifiedSamplingParams userSpecParams) throws IOException;
	
	
	/**
	 * Return a map of name to description of all the policies in the system
	 * This is used to drive a dropdown in the UI.
	 * @return
	 * @throws IOException
	 */
	public HashMap<String, String> getPoliciesInInstallation() throws IOException;
	
	
	/**
	 * This product offers the ability to archive certain fields (like HIHI, LOLO etc) as part of every PV.
	 * The data for these fields is embedded into the stream as extra fields using the FieldValues interface of events.
	 * This method lists all these fields.
	 * Requests for archiving these fields are deferred to and combined with the request for archiving the .VAL.
	 * We also assume that the data type (double/float) for these fields is the same as the .VAL.  
	 * @return
	 * @throws IOException
	 */
	public List<String> getFieldsArchivedAsPartOfStream() throws IOException;
	
	
	/**
	 * Returns a TypeSystem object that is used to convert from JCA DBR's to Event's (actually, DBRTimeEvents)
	 * @return
	 */
	public TypeSystem getArchiverTypeSystem();
	
	
	/**
	 * Which component is this configservice instance.
	 * @return
	 */
	public WAR_FILE getWarFile();

	/**
	 * Returns the runtime state for the retrieval app
	 * @return
	 */
	public RetrievalState getRetrievalRuntimeState();
	
	
	/**
	 * Return the runtime state for ETL.
	 * This may eventually be moved to a RunTime class but that would still start from the configservice.
	 * @return
	 */
	public PBThreeTierETLPVLookup getETLLookup();
	
	
	/**
	 * Return the runtime state for the engine. 
	 * @return
	 */
	public EngineContext getEngineContext();
	
	
	/**
	 * Return the runtime state for the mgmt webapp.
	 * @return
	 */
	public MgmtRuntimeState getMgmtRuntimeState();
	
	/**
	 * Is this appliance component shutting down?
	 * @return
	 */
	public boolean isShuttingDown();
	
	/**
	 * Add an appserver agnostic shutdown hook; for example, to close the CA channels on shutdown
	 * @param runnable
	 */
	public void addShutdownHook(Runnable runnable);
	
	
	/**
	 * Call the registered shutdown hooks and shut the archive appliance down.
	 */
	public void shutdownNow();
	
	
	/**
	 * Get the event bus used for events within this appliance.
	 * @return
	 */
	public EventBus getEventBus();
	
	
	/**
	 * Get a list of external Channel Archiver Data Servers that we know about.
	 * @return
	 */
	public Map<String, String> getChannelArchiverDataServers();


	/**
	 * Add a external Channel Archiver Data Server into the system
	 * @param serverURL - URL to the XML-RPC server
	 * @param archivesCSV - A comma separated list of indexes
	 */
	public void addChannelArchiverDataServer(String serverURL, String archivesCSV) throws IOException;

	/**
	 * Removes an entry for an external Channel Archiver Data Server from the system
	 * Note; we may need to restart the entire cluster for this change to take effect.
	 * @param serverURL - URL to the XML-RPC server
	 * @param archivesCSV - A comma separated list of indexes
	 */
	public void removeChannelArchiverDataServer(String serverURL, String archivesCSV) throws IOException;

	/**
	 * Return a list of ChannelArchiverDataServerPVInfos for a PV if one exists; otherwise return null.
	 * The servers are sorted in order of the start seconds. 
	 * 
	 * @param pvName
	 */
	public List<ChannelArchiverDataServerPVInfo> getChannelArchiverDataServers(String pvName);
	
	
	/**
	 * For all the ChannelArchiver servers in the mix, update the PV info. 
	 * This should help a little in proxying data from ChannelArchiver data servers that are still active.
	 */
	public void refreshPVDataFromChannelArchiverDataServers();
	
	
	/**
	 *  Implementation for converting a PV name to something that forms the prefix of a chunk's key.
	 *  See @see{PVNameToKeyMapping} for more details.
	 * @return
	 */
	public PVNameToKeyMapping getPVNameToKeyConverter();
	
	
	// Various reporting helper functions start here
	
	/**
	 * Get a set of PVs that have been paused in this appliance.
	 * @return
	 */
	public Set<String> getPausedPVsInThisAppliance();
	
	
}

