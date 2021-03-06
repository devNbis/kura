/**
 * Copyright (c) 2011, 2014 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 */
package org.eclipse.kura.net.admin.monitor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.comm.CommURI;
import org.eclipse.kura.linux.net.ConnectionInfoImpl;
import org.eclipse.kura.linux.net.modem.SupportedSerialModemInfo;
import org.eclipse.kura.linux.net.modem.SupportedSerialModemsInfo;
import org.eclipse.kura.linux.net.modem.SupportedUsbModemInfo;
import org.eclipse.kura.linux.net.modem.SupportedUsbModemsInfo;
import org.eclipse.kura.linux.net.util.LinuxNetworkUtil;
import org.eclipse.kura.net.ConnectionInfo;
import org.eclipse.kura.net.NetConfig;
import org.eclipse.kura.net.NetConfigIP4;
import org.eclipse.kura.net.NetInterface;
import org.eclipse.kura.net.NetInterfaceAddress;
import org.eclipse.kura.net.NetInterfaceStatus;
import org.eclipse.kura.net.NetworkAdminService;
import org.eclipse.kura.net.NetworkService;
import org.eclipse.kura.net.admin.event.NetworkConfigurationChangeEvent;
import org.eclipse.kura.net.admin.event.NetworkStatusChangeEvent;
import org.eclipse.kura.net.admin.modem.CellularModemFactory;
import org.eclipse.kura.net.admin.modem.EvdoCellularModem;
import org.eclipse.kura.net.admin.modem.HspaCellularModem;
import org.eclipse.kura.net.admin.modem.IModemLinkService;
import org.eclipse.kura.net.admin.modem.PppFactory;
import org.eclipse.kura.net.admin.modem.PppState;
import org.eclipse.kura.net.admin.modem.SupportedSerialModemsFactoryInfo;
import org.eclipse.kura.net.admin.modem.SupportedSerialModemsFactoryInfo.SerialModemFactoryInfo;
import org.eclipse.kura.net.admin.modem.SupportedUsbModemsFactoryInfo;
import org.eclipse.kura.net.admin.modem.SupportedUsbModemsFactoryInfo.UsbModemFactoryInfo;
import org.eclipse.kura.net.modem.CellularModem;
import org.eclipse.kura.net.modem.ModemAddedEvent;
import org.eclipse.kura.net.modem.ModemConfig;
import org.eclipse.kura.net.modem.ModemDevice;
import org.eclipse.kura.net.modem.ModemGpsDisabledEvent;
import org.eclipse.kura.net.modem.ModemGpsEnabledEvent;
import org.eclipse.kura.net.modem.ModemInterface;
import org.eclipse.kura.net.modem.ModemManagerService;
import org.eclipse.kura.net.modem.ModemMonitorListener;
import org.eclipse.kura.net.modem.ModemMonitorService;
import org.eclipse.kura.net.modem.ModemReadyEvent;
import org.eclipse.kura.net.modem.ModemRemovedEvent;
import org.eclipse.kura.net.modem.ModemTechnologyType;
import org.eclipse.kura.net.modem.SerialModemDevice;
import org.eclipse.kura.system.SystemService;
import org.eclipse.kura.usb.UsbDeviceEvent;
import org.eclipse.kura.usb.UsbModemDevice;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModemMonitorServiceImpl implements ModemMonitorService, ModemManagerService, EventHandler {
	
	private static final Logger s_logger = LoggerFactory.getLogger(ModemMonitorServiceImpl.class);
	
	private ComponentContext      m_ctx;
	private final static String[] EVENT_TOPICS = new String[] {
			NetworkConfigurationChangeEvent.NETWORK_EVENT_CONFIG_CHANGE_TOPIC,
			ModemAddedEvent.MODEM_EVENT_ADDED_TOPIC,
			ModemRemovedEvent.MODEM_EVENT_REMOVED_TOPIC, };
	
	private final static long THREAD_INTERVAL = 30000;
	private final static long THREAD_TERMINATION_TOUT = 1; // in seconds
	
	private static Future<?>  task;
	private static boolean stopThread;

	private SystemService m_systemService;
	private NetworkService m_networkService;
	private NetworkAdminService m_networkAdminService;
	private EventAdmin m_eventAdmin;
	
	private List<ModemMonitorListener>m_listeners;
	
	private ExecutorService m_executor;
	
	private Map<String, CellularModem> m_modems;
	private Map<String, InterfaceState> m_interfaceStatuses;
	
	private Boolean m_gpsSupported = null;
	
	private boolean m_serviceActivated; 
	
	private PppState m_pppState;
	private long m_resetTimerStart;
	    
    public void setNetworkService(NetworkService networkService) {
        m_networkService = networkService;
    }
    
    public void unsetNetworkService(NetworkService networkService) {
        m_networkService = null;
    }

    public void setEventAdmin(EventAdmin eventAdmin) {
        m_eventAdmin = eventAdmin;
    }

    public void unsetEventAdmin(EventAdmin eventAdmin) {
        m_eventAdmin = null;
    }
    
    public void setNetworkAdminService(NetworkAdminService networkAdminService) {
    	m_networkAdminService = networkAdminService;
    }
    
    public void unsetNetworkAdminService(NetworkAdminService networkAdminService) {
    	m_networkAdminService = null;
    }
        
	public void setSystemService(SystemService systemService) {
		m_systemService = systemService;
	}

	public void unsetSystemService(SystemService systemService) {
		m_systemService = null;
	}
	
    protected void activate(ComponentContext componentContext)  {
    	
    	// save the bundle context
    	m_ctx = componentContext;
    	
    	m_pppState = PppState.NOT_CONNECTED;
    	m_resetTimerStart = 0L;
    	
    	Dictionary<String, String[]> d = new Hashtable<String, String[]>();
    	d.put(EventConstants.EVENT_TOPIC, EVENT_TOPICS);
    	m_ctx.getBundleContext().registerService(EventHandler.class.getName(), this, d);
    	
		m_modems = new HashMap<String, CellularModem>();
		m_interfaceStatuses = new HashMap<String, InterfaceState>();
		m_listeners = new ArrayList<ModemMonitorListener>();
		
		// track currently installed modems
		try {
			for(NetInterface<? extends NetInterfaceAddress> netInterface : m_networkService.getNetworkInterfaces()) {
				if(netInterface instanceof ModemInterface) {
					ModemDevice modemDevice = ((ModemInterface<?>) netInterface).getModemDevice();
					trackModem(modemDevice);
				}
			}
		} catch (Exception e) {
			s_logger.error("Error getting installed modems", e);
		}
		
		stopThread = false;
		m_executor = Executors.newSingleThreadExecutor();
		task = m_executor.submit(new Runnable() {
    		@Override
    		public void run() {
    			while (!stopThread) {
	    			Thread.currentThread().setName("ModemMonitor");
	    			try {
	    				monitor();
	    				Thread.sleep(THREAD_INTERVAL);
					} catch (InterruptedException e) {
						s_logger.debug(e.getMessage());
					} catch (Throwable t) {
						s_logger.error("activate() :: Exception while monitoring cellular connection {}", t.toString());
						t.printStackTrace();
					}
    			}
    	}});
		
		m_serviceActivated = true;
		s_logger.debug("ModemMonitor activated and ready to receive events");
    }
    
    protected void deactivate(ComponentContext componentContext) {
    	m_listeners = null;
    	stopThread = true;
    	PppFactory.releaseAllPppServices();
    	if ((task != null) && (!task.isDone())) {
    		s_logger.debug("Cancelling ModemMonitor task ...");
    		task.cancel(true);
    		s_logger.info("ModemMonitor task cancelled? = {}", task.isDone());
    		task = null;
    	}
    	
    	if (m_executor != null) {
    		s_logger.debug("Terminating ModemMonitor Thread ...");
    		m_executor.shutdownNow();
    		try {
				m_executor.awaitTermination(THREAD_TERMINATION_TOUT, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				s_logger.warn("Interrupted", e);
			}
    		s_logger.info("ModemMonitor Thread terminated? - {}", m_executor.isTerminated());
			m_executor = null;
    	}
    	m_serviceActivated = false;
	}
    
    @Override
	public void handleEvent(Event event) {
    	s_logger.debug("handleEvent - topic: {}", event.getTopic());
        String topic = event.getTopic();
        if (topic.equals(NetworkConfigurationChangeEvent.NETWORK_EVENT_CONFIG_CHANGE_TOPIC)) {
        	ExecutorService ex = Executors.newSingleThreadExecutor();
    		ex.submit(new Runnable() {
        		@Override
        		public void run() {
        			processNetworkConfigurationChangeEvent();
        	}});
        } else if (topic.equals(ModemAddedEvent.MODEM_EVENT_ADDED_TOPIC)) {
        	
        	ModemAddedEvent modemAddedEvent = (ModemAddedEvent)event;
        	final ModemDevice modemDevice = modemAddedEvent.getModemDevice();
        	if (m_serviceActivated) {
        		ExecutorService ex = Executors.newSingleThreadExecutor();
        		ex.submit(new Runnable() {
            		@Override
            		public void run() {
            			trackModem(modemDevice);
            	}});
        	}
        } else if (topic.equals(ModemRemovedEvent.MODEM_EVENT_REMOVED_TOPIC)) {
        	ModemRemovedEvent modemRemovedEvent = (ModemRemovedEvent)event;
        	String usbPort = (String)modemRemovedEvent.getProperty(UsbDeviceEvent.USB_EVENT_USB_PORT_PROPERTY);
        	m_modems.remove(usbPort);
        }	
	}

	
    @Override
	public CellularModem getModemService (String usbPort) {
		return m_modems.get(usbPort);
	}
    
    @Override
    public Collection<CellularModem> getAllModemServices() {
    	return m_modems.values();
    }
    
	private NetInterfaceStatus getNetInterfaceStatus (List<NetConfig> netConfigs) {
		
		NetInterfaceStatus interfaceStatus = NetInterfaceStatus.netIPv4StatusUnknown;
		if ((netConfigs != null) && (netConfigs.size() > 0)) {
			for (NetConfig netConfig : netConfigs) {
				if (netConfig instanceof NetConfigIP4) {
					interfaceStatus = ((NetConfigIP4) netConfig).getStatus();
					break;
				}
			}
		}
		return interfaceStatus;
	}
	
	@Override
	public void registerListener(ModemMonitorListener newListener) {
		boolean found = false;
		if (m_listeners == null) {
			m_listeners = new ArrayList<ModemMonitorListener>();
		}
		if (m_listeners.size() > 0) {
			for (ModemMonitorListener listener : m_listeners) {
				if (listener.equals(newListener)) {
					found = true;
					break;
				}
			}
		}
		if (!found) {
			m_listeners.add(newListener);
		}
	}
	
	@Override
	public void unregisterListener(ModemMonitorListener listenerToUnregister) {
		if ((m_listeners != null) && (m_listeners.size() > 0)) {
			
			for (int i = 0; i < m_listeners.size(); i++) {
				if (((ModemMonitorListener)m_listeners.get(i)).equals(listenerToUnregister)) {
					m_listeners.remove(i);
				}
			}
		}
	}
	
	private void processNetworkConfigurationChangeEvent() {
    	
    	Set<String> keySet = m_modems.keySet();
		Iterator<String> keySetItetrator = keySet.iterator();
		while (keySetItetrator.hasNext()) {
			String usbPort = keySetItetrator.next();
			CellularModem modem = m_modems.get(usbPort);
			try {
				String ifaceName = null;
    			if (m_networkService != null) {
    				ifaceName = m_networkService.getModemPppPort(modem.getModemDevice());
    			}
    			if (ifaceName != null) {
	    			List<NetConfig> oldNetConfigs = modem.getConfiguration();
	    			List<NetConfig>newNetConfigs = m_networkAdminService.getNetworkInterfaceConfigs(ifaceName);
	    			if ((oldNetConfigs == null) || !oldNetConfigs.equals(newNetConfigs)) {
	    				s_logger.info("new configuration for cellular modem on usb port {} netinterface {}", usbPort, ifaceName); 
	    				int ifaceNo = getInterfaceNumber(oldNetConfigs);
	    				if (ifaceNo >= 0) {
	    					IModemLinkService pppService = PppFactory.obtainPppService(ifaceNo, modem.getDataPort());
	    					if (pppService != null) {
	    						PppState pppState = pppService.getPppState();
								if ((pppState == PppState.CONNECTED) || (pppState == PppState.IN_PROGRESS)) {
									s_logger.info("disconnecting " + pppService.getIfaceName());
									pppService.disconnect();
								}
								PppFactory.releasePppService(pppService.getIfaceName());
	    					}
	    				}
	    				
	    				if (modem.isGpsEnabled()) {
	    					if (!disableModemGps(modem)) {
	    						s_logger.error("processNetworkConfigurationChangeEvent() :: Failed to disable modem GPS");
	    						modem.reset();
	    					}
	    				}
	    				
	    				modem.setConfiguration(newNetConfigs);
	    				
	    				if (modem instanceof EvdoCellularModem) {
		    				NetInterfaceStatus netIfaceStatus = getNetInterfaceStatus(newNetConfigs);
							if (netIfaceStatus == NetInterfaceStatus.netIPv4StatusEnabledWAN) {
										
			    				if (m_gpsSupported == null) {
			    					boolean gpsSupported = modem.isGpsSupported();
			    					m_gpsSupported = gpsSupported;
			    				}
			    				
								if (!((EvdoCellularModem) modem).isProvisioned()) {
									s_logger.info("NetworkConfigurationChangeEvent :: The {} is not provisioned, will try to provision it ...", modem.getModel());
									
									if ((task != null) && !task.isCancelled()) {
										s_logger.info("NetworkConfigurationChangeEvent :: Cancelling monitor task");
										stopThread = true;
										task.cancel(true);
										task = null;
									}
									
									((EvdoCellularModem) modem).provision();
									if (task == null) {
										s_logger.info("NetworkConfigurationChangeEvent :: Restarting monitor task");
										stopThread = false;
										task = m_executor.submit(new Runnable() {
								    		@Override
								    		public void run() {
								    			while (!stopThread) {
								    				Thread.currentThread().setName("ModemMonitor");
								    				try {
								    					monitor();
														Thread.sleep(THREAD_INTERVAL);
													} catch (InterruptedException e) {
														s_logger.debug(e.getMessage());
													} catch (Throwable t) {
														s_logger.error("handleEvent() :: Exception while monitoring cellular connection {}", t.toString());
														t.printStackTrace();
													}
								    			}
								    	}});
									}
								} else {
									s_logger.info("NetworkConfigurationChangeEvent :: The " + modem.getModel() + " is provisioned");
								}	
							}
							
							s_logger.debug("monitor() :: gpsSupported={}", m_gpsSupported);
							if ((m_gpsSupported != null) && m_gpsSupported) {	
								List<NetConfig> netConfigs = m_networkAdminService.getNetworkInterfaceConfigs(ifaceName);
								if (isGpsEnabledInConfig(netConfigs) && !modem.isGpsEnabled()) {
									enableModemGps(modem);
								}
							}
	    				}
	    			}
    			}
			} catch (KuraException e) {
				e.printStackTrace();
			}
		}
    }
	
	private int getInterfaceNumber (List<NetConfig> netConfigs) {
		int ifaceNo = -1;
		if ((netConfigs != null) && (netConfigs.size() > 0)) {
			for (NetConfig netConfig : netConfigs) {
				if (netConfig instanceof ModemConfig) {
					ifaceNo = ((ModemConfig) netConfig).getPppNumber();
					break;
				}
			}
		}
		return ifaceNo;
	}
	
	private long getModemResetTimeoutMsec(String ifaceName) {
		long resetToutMsec = 0L;
		
		if (ifaceName != null) {
			try {
				List<NetConfig> netConfigs = m_networkAdminService.getNetworkInterfaceConfigs(ifaceName);
				if ((netConfigs != null) && (netConfigs.size() > 0)) {
					for (NetConfig netConfig : netConfigs) {
						if (netConfig instanceof ModemConfig) {
							resetToutMsec = ((ModemConfig) netConfig).getResetTimeout() * 60000;
							break;
						}
					}
				}
			} catch (KuraException e) {
				e.printStackTrace();
			}
		}
		return resetToutMsec;
	}
	
	private boolean isGpsEnabledInConfig(List<NetConfig> netConfigs) {
		boolean isGpsEnabled = false;
		if ((netConfigs != null) && (netConfigs.size() > 0)) {
			for (NetConfig netConfig : netConfigs) {
				if (netConfig instanceof ModemConfig) {
					isGpsEnabled = ((ModemConfig) netConfig).isGpsEnabled();
					break;
				}
			}
		}
		return isGpsEnabled;
	}
	
 	private void monitor() {
 		HashMap<String, InterfaceState> newInterfaceStatuses = new HashMap<String, InterfaceState>();
		Set<String> keySet = m_modems.keySet();
		Iterator<String> keySetItetrator = keySet.iterator();
		while (keySetItetrator.hasNext()) {
			String usbPort = keySetItetrator.next();
			CellularModem modem = m_modems.get(usbPort);
			
			// get signal strength only if somebody needs it
			if ((m_listeners != null) && (m_listeners.size() > 0)) {
				for (ModemMonitorListener listener : m_listeners) {
					try {
						int rssi = modem.getSignalStrength();
						listener.setCellularSignalLevel(rssi);
					} catch (KuraException e) {
						listener.setCellularSignalLevel(0);
						e.printStackTrace();
					}
				}
			}
			
			IModemLinkService pppService = null;
			PppState pppState = null;
			NetInterfaceStatus netInterfaceStatus = getNetInterfaceStatus(modem.getConfiguration());
			try {
				String ifaceName = m_networkService.getModemPppPort(modem.getModemDevice());
				if (netInterfaceStatus == NetInterfaceStatus.netIPv4StatusEnabledWAN) {				
					if (ifaceName != null) {
						pppService = PppFactory.obtainPppService(ifaceName, modem.getDataPort());
						pppState = pppService.getPppState();
						
						if (m_pppState != pppState) {
							s_logger.info("monitor() :: previous PppState={}", m_pppState);
							s_logger.info("monitor() :: current PppState={}", pppState);
						}
						
						if (pppState == PppState.NOT_CONNECTED) {
							if (modem.getTechnologyType() == ModemTechnologyType.HSDPA) {
								if(((HspaCellularModem)modem).isSimCardReady()) {
									s_logger.info("monitor() :: !!! SIM CARD IS READY !!! connecting ...");
									pppService.connect();
									if (m_pppState == PppState.NOT_CONNECTED) {
										m_resetTimerStart = System.currentTimeMillis();
									}
								}
							} else {
								s_logger.info("monitor() :: connecting ...");
								pppService.connect();
								if (m_pppState == PppState.NOT_CONNECTED) {
									m_resetTimerStart = System.currentTimeMillis();
								}
							}
						} else if (pppState == PppState.IN_PROGRESS) {
							long modemResetTout = getModemResetTimeoutMsec(ifaceName);
							if (modemResetTout > 0) {
								long timeElapsed = System.currentTimeMillis() - m_resetTimerStart;
								if (timeElapsed > modemResetTout) {
									// reset modem
									s_logger.info("monitor() :: Modem Reset TIMEOUT !!!");
									pppService.disconnect();
									if (modem.isGpsEnabled()) {
										if (!disableModemGps(modem)) {
											s_logger.error("monitor() :: Failed to disable modem GPS");
										}
									}
									modem.reset();
								} else {
									int timeTillReset = (int)(modemResetTout - timeElapsed) / 1000;
									s_logger.info("monitor() :: PPP connection in progress. Modem will be reset in {} sec if not connected", timeTillReset);
								}
							}
						} else if (pppState == PppState.CONNECTED) {
							m_resetTimerStart = System.currentTimeMillis();
						}
						
						m_pppState = pppState;
						ConnectionInfo connInfo = new ConnectionInfoImpl(ifaceName);
						InterfaceState interfaceState = new InterfaceState(ifaceName, 
								LinuxNetworkUtil.isUp(ifaceName), 
								pppState == PppState.CONNECTED, 
								connInfo.getIpAddress());
						newInterfaceStatuses.put(ifaceName, interfaceState);
					}
				}  
				
				s_logger.debug("monitor() :: gpsSupported={}", m_gpsSupported);
				if ((m_gpsSupported != null) && m_gpsSupported) {
					List<NetConfig> netConfigs = m_networkAdminService.getNetworkInterfaceConfigs(ifaceName);
					if (isGpsEnabledInConfig(netConfigs)) {
						if (modem instanceof HspaCellularModem) {
							if (!modem.isGpsEnabled()) {
								enableModemGps(modem);
							} else {
								postModemGpsEvent(modem, true);
							}
						} else {
							postModemGpsEvent(modem, true);
						}
					}
				}
			} catch (Exception e) {
				s_logger.error("monitor() :: Exception -> " + e);
				if ((pppService != null) && (pppState != null)) {
					try {
						s_logger.error("monitor() :: Exception :: PPPD disconnect");
						pppService.disconnect();
					} catch (KuraException e1) {
						e1.printStackTrace();
					}
					m_pppState = pppState;
				}
				
				if (modem.isGpsEnabled()) {
					try {
						if (!disableModemGps(modem)) {
							s_logger.error("monitor() :: Failed to disable modem GPS");
						}
					} catch (KuraException e1) {
						e1.printStackTrace();
					}
				}
				
				try {
					s_logger.error("monitor() :: Exception :: modem reset");
					modem.reset();
				} catch (KuraException e1) {
					e1.printStackTrace();
				}
				e.printStackTrace();
			}
		}
		
		// post event for any status changes
		checkStatusChange(m_interfaceStatuses, newInterfaceStatuses);
		m_interfaceStatuses = newInterfaceStatuses;
 	}
 	
    private void checkStatusChange(Map<String, InterfaceState> oldStatuses, Map<String, InterfaceState> newStatuses) {
		
		if (newStatuses != null) {
	        // post NetworkStatusChangeEvent on current and new interfaces
			for(String interfaceName : newStatuses.keySet()) {
				if ((oldStatuses != null) && oldStatuses.containsKey(interfaceName)) {
					if (!newStatuses.get(interfaceName).equals(oldStatuses.get(interfaceName))) {
						s_logger.debug("Posting NetworkStatusChangeEvent on interface: {}", interfaceName);
						m_eventAdmin.postEvent(new NetworkStatusChangeEvent(interfaceName, newStatuses.get(interfaceName), null));
					}
				} else {
					s_logger.debug("Posting NetworkStatusChangeEvent on enabled interface: " + interfaceName);
					m_eventAdmin.postEvent(new NetworkStatusChangeEvent(interfaceName, newStatuses.get(interfaceName), null));
				}
			}
	        
	        // post NetworkStatusChangeEvent on interfaces that are no longer there
	        if (oldStatuses != null) {
	        	for(String interfaceName : oldStatuses.keySet()) {
                    if(!newStatuses.containsKey(interfaceName)) {
                        s_logger.debug("Posting NetworkStatusChangeEvent on disabled interface: {}", interfaceName);
                        m_eventAdmin.postEvent(new NetworkStatusChangeEvent(interfaceName, oldStatuses.get(interfaceName), null));
                    }
                }
	        }
		}
	}
    
	private void trackModem(ModemDevice modemDevice) {
		
		Class<? extends CellularModemFactory> modemFactoryClass = null;
		
		if (modemDevice instanceof UsbModemDevice) {
			SupportedUsbModemInfo supportedUsbModemInfo = SupportedUsbModemsInfo.getModem((UsbModemDevice)modemDevice);
			UsbModemFactoryInfo usbModemFactoryInfo = SupportedUsbModemsFactoryInfo.getModem(supportedUsbModemInfo);
			modemFactoryClass = usbModemFactoryInfo.getModemFactoryClass();
		} else if (modemDevice instanceof SerialModemDevice) {
			SupportedSerialModemInfo supportedSerialModemInfo = SupportedSerialModemsInfo.getModem();
			SerialModemFactoryInfo serialModemFactoryInfo = SupportedSerialModemsFactoryInfo.getModem(supportedSerialModemInfo);
			modemFactoryClass = serialModemFactoryInfo.getModemFactoryClass();
		}
		
		if (modemFactoryClass != null) {
			CellularModemFactory modemFactoryService = null;
			try {
				try {
					Method getInstanceMethod = modemFactoryClass.getDeclaredMethod("getInstance", (Class<?>[]) null);
					getInstanceMethod.setAccessible(true);
					modemFactoryService = (CellularModemFactory) getInstanceMethod.invoke(null, (Object[]) null);
				} catch (Exception e) {
					s_logger.error("Error calling getInstance() method on " + modemFactoryClass.getName() + e);
				}
				
				// if unsuccessful in calling getInstance()
				if (modemFactoryService == null) {
					modemFactoryService = (CellularModemFactory) modemFactoryClass.newInstance();
				}
				
				String platform = null;
				if(m_systemService != null) {
					platform = m_systemService.getPlatform();
				}
				CellularModem modem = modemFactoryService.obtainCellularModemService(modemDevice, platform);
				
				try {
					HashMap<String, String> modemInfoMap = new HashMap<String, String>();
					modemInfoMap.put(ModemReadyEvent.IMEI, modem.getSerialNumber());
					modemInfoMap.put(ModemReadyEvent.IMSI, modem.getMobileSubscriberIdentity());
					modemInfoMap.put(ModemReadyEvent.ICCID, modem.getIntegratedCirquitCardId());
					s_logger.info("posting ModemReadyEvent on topic {}", ModemReadyEvent.MODEM_EVENT_READY_TOPIC);
					m_eventAdmin.postEvent(new ModemReadyEvent(modemInfoMap));
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				String ifaceName = m_networkService.getModemPppPort(modemDevice);
				List<NetConfig> netConfigs = null;
				if (ifaceName != null) {
					netConfigs = m_networkAdminService.getNetworkInterfaceConfigs(ifaceName);
					if ((netConfigs != null) && (netConfigs.size() > 0)) {
						modem.setConfiguration(netConfigs);
					}
				}
				
				if (m_gpsSupported == null) {
					try {
						boolean gpsSupported = modem.isGpsSupported();
						m_gpsSupported = gpsSupported;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				if (modemDevice instanceof UsbModemDevice) {
					m_modems.put(((UsbModemDevice)modemDevice).getUsbPort(), modem);
				} else if (modemDevice instanceof SerialModemDevice) {
					m_modems.put(modemDevice.getProductName(), modem);
				}
				
				if (modem instanceof EvdoCellularModem) {
					NetInterfaceStatus netIfaceStatus = getNetInterfaceStatus(netConfigs);
					if (netIfaceStatus == NetInterfaceStatus.netIPv4StatusEnabledWAN) {
						if (modem.isGpsEnabled()) {
							if (!disableModemGps(modem)) {
								s_logger.error("trackModem() :: Failed to disable modem GPS, resetting modem ...");
								modem.reset();
							}
						}
							
						if (!((EvdoCellularModem) modem).isProvisioned()) {
							s_logger.info("trackModem() :: The {} is not provisioned, will try to provision it ...", modem.getModel());
							if ((task != null) && !task.isCancelled()) {
								s_logger.info("trackModem() :: Cancelling monitor task");
								stopThread = true;
								task.cancel(true);
								task = null;
							}
							((EvdoCellularModem) modem).provision();
							if (task == null) {
								s_logger.info("trackModem() :: Restarting monitor task");
								stopThread = false;
								task = m_executor.submit(new Runnable() {
							    	@Override
							    	public void run() {
							    		while (!stopThread) {
							    			Thread.currentThread().setName("ModemMonitor");
							    			try {
							    				monitor();
							    				Thread.sleep(THREAD_INTERVAL);
							    			} catch (InterruptedException e) {
												s_logger.debug(e.getMessage());
											} catch (Throwable t) {
												s_logger.error("trackModem() :: Exception while monitoring cellular connection {}", t.toString());
												t.printStackTrace();
											}
							    		}
							    }});
							}
						} else {
							s_logger.info("trackModem() :: The {} is provisioned", modem.getModel());
						}
					}
					
					s_logger.debug("trackModem() :: gpsSupported={}", m_gpsSupported);
					if ((m_gpsSupported != null) && m_gpsSupported) {	
						if (isGpsEnabledInConfig(netConfigs) && !modem.isGpsEnabled()) {
								enableModemGps(modem);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void enableModemGps(CellularModem modem) throws KuraException {
		
		modem.enableGps();
		postModemGpsEvent(modem, true);
	}
	
	private boolean disableModemGps(CellularModem modem) throws KuraException {
		
		postModemGpsEvent(modem, false);
		
		boolean portIsReachable = false;
		long startTimer = System.currentTimeMillis();	
		do {
			try {
				Thread.sleep(3000);
				if (modem.isPortReachable(modem.getGpsPort())) {
					s_logger.debug("disableModemGps() modem is now reachable ...");
					portIsReachable = true;
					break;
				} else {
					s_logger.debug("disableModemGps() waiting for PositionService to release serial port ...");
				}
			} catch (Exception e) {
				s_logger.debug("disableModemGps() waiting for PositionService to release serial port: ex={}", e);
			}
		} while ((System.currentTimeMillis()-startTimer) < 20000L);
		
		modem.disableGps();
		try {
			Thread.sleep(1000);
		} catch(InterruptedException e) {}
		
		boolean ret = false;
		if (portIsReachable && !modem.isGpsEnabled()) {
			s_logger.error("disableModemGps() :: Failed to disable modem GPS :: portIsReachable={}, modem.isGpsEnabled()={}",
					portIsReachable, modem.isGpsEnabled());
			ret = true;
		}
		return ret;
	}
	
	private void postModemGpsEvent(CellularModem modem, boolean enabled) throws KuraException {
		
		if (enabled) {
			CommURI commUri = modem.getSerialConnectionProperties(CellularModem.SerialPortType.GPSPORT);
			s_logger.trace("postModemGpsEvent() :: Modem SeralConnectionProperties: {}", commUri.toString());			
			
			HashMap<String, Object> modemInfoMap = new HashMap<String, Object>();
			modemInfoMap.put(ModemGpsEnabledEvent.Port, modem.getGpsPort());
			modemInfoMap.put(ModemGpsEnabledEvent.BaudRate, new Integer(commUri.getBaudRate()));
			modemInfoMap.put(ModemGpsEnabledEvent.DataBits, new Integer(commUri.getDataBits()));
			modemInfoMap.put(ModemGpsEnabledEvent.StopBits, new Integer(commUri.getStopBits()));
			modemInfoMap.put(ModemGpsEnabledEvent.Parity, new Integer(commUri.getParity()));
			
			s_logger.info("postModemGpsEvent() :: posting ModemGpsEnabledEvent on topic {}", ModemGpsEnabledEvent.MODEM_EVENT_GPS_ENABLED_TOPIC);
			m_eventAdmin.postEvent(new ModemGpsEnabledEvent(modemInfoMap));
		} else {
			s_logger.info("postModemGpsEvent() :: posting ModemGpsDisableEvent on topic {}", ModemGpsDisabledEvent.MODEM_EVENT_GPS_DISABLED_TOPIC);
			HashMap<String, Object> modemInfoMap = new HashMap<String, Object>();
			m_eventAdmin.postEvent(new ModemGpsDisabledEvent(modemInfoMap));
		}
	}
}
