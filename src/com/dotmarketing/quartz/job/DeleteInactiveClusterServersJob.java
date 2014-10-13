package com.dotmarketing.quartz.job;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import com.dotcms.cluster.bean.Server;
import com.dotcms.enterprise.LicenseUtil;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotHibernateException;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;

/**
 * This class will remove from the cluster_table and cluster_server_uptime
 * the servers that are not working 
 * @author oswaldogallango
 *
 */
public class DeleteInactiveClusterServersJob implements StatefulJob {
	private final String DEFAULT_UNIT="WEEKS";
	private final int DEFAULT_TIME=1;
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		try {
			int amount = Config.getIntProperty("REMOVE_INACTIVE_CLUSTER_SERVER_AMOUNT",DEFAULT_TIME);
			String unit = Config.getStringProperty("REMOVE_INACTIVE_CLUSTER_SERVER_UNIT",DEFAULT_UNIT);
			long maxAmountOfTime=0;
			if(unit.equals("MINUTES")){
				maxAmountOfTime = amount * 1000 * 60;
			}else if(unit.equals("HOURS")){
				maxAmountOfTime = amount * 1000 * 60 * 60;
			}else if(unit.equals("DAYS")){
				maxAmountOfTime = amount * 1000 * 60 * 60 * 24;
			}else{
				//weeks to millis seconds
				maxAmountOfTime = amount * 1000 * 60 * 60 * 24 * 7;
			}
			
			List<Server> inactiveServers = APILocator.getServerAPI().getInactiveServers();
			Date currentDate = new Date();
			for(Server server : inactiveServers){
				Date lastBeat = server.getLastHeartBeat();
				long timeOff = currentDate.getTime() - lastBeat.getTime();
				if(timeOff >= maxAmountOfTime){
					for(Map<String, Object> lic : LicenseUtil.getLicenseRepoList()){
						if( server.getServerId().equals((String)lic.get("serverid"))) {
							LicenseUtil.deleteLicense((String)lic.get( "id" ) );
							break;
						}
					}
					APILocator.getServerAPI().removeServer(server.getServerId());
				}
			}
		} catch (DotDataException e) {
			Logger.error(DeleteInactiveClusterServersJob.class, "Could not remove inactive cluster servers", e);
		} catch (IOException e) {
			Logger.error(DeleteInactiveClusterServersJob.class, "Could not remove inactive cluster servers", e);
		}
		finally {
			try {
				HibernateUtil.closeSession();
			} catch (DotHibernateException e) {
				Logger.warn(this, e.getMessage(), e);
			}
			finally {
				DbConnectionFactory.closeConnection();
			}
		}

	}
}
