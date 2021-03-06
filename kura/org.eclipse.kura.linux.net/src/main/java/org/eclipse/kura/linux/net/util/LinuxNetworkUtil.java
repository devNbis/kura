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
package org.eclipse.kura.linux.net.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.core.linux.util.LinuxProcessUtil;
import org.eclipse.kura.core.util.ProcessUtil;
import org.eclipse.kura.linux.net.NetworkServiceImpl;
import org.eclipse.kura.linux.net.wifi.WifiOptions;
import org.eclipse.kura.net.NetInterfaceType;
import org.eclipse.kura.net.wifi.WifiInterface.Capability;
import org.eclipse.kura.net.wifi.WifiMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinuxNetworkUtil {

	private static final Logger s_logger = LoggerFactory.getLogger(LinuxNetworkUtil.class);
	
	private static final String OS_VERSION = System.getProperty("kura.os.version");

	public static List<String> getInterfaceNames() throws KuraException {
		Process proc = null;
		try {
			List<String> ifaces = new ArrayList<String>();

			//start the process
			proc = ProcessUtil.exec("ifconfig");

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				if(line.indexOf("Link encap:") > -1) {
					StringTokenizer st = new StringTokenizer(line);
					ifaces.add(st.nextToken());
				}
			}

			br.close();
			br = null;

			try {
				if (proc.waitFor() != 0) {
					s_logger.error("error executing command --- ifconfig --- exit value = " + proc.exitValue());
					return null;
				} else {
					return ifaces;
				}
			} catch (InterruptedException e) {
				s_logger.error("getInterfaceNames() :: InterruptedException");
				return ifaces;
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} 
		finally {
			ProcessUtil.destroy(proc);
		}
	}

	public static List<String> getAllInterfaceNames() throws KuraException {
		Process proc = null;
		try {
			List<String> ifaces = new ArrayList<String>();

			//start the process
			proc = ProcessUtil.exec("ifconfig -a");

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				if(line.indexOf("Link encap:") > -1) {
					StringTokenizer st = new StringTokenizer(line);
					ifaces.add(st.nextToken());
				}
			}

			br.close();
			br = null;

			try {
				if (proc.waitFor() != 0) {
					s_logger.error("error executing command --- ifconfig -a --- exit value = " + proc.exitValue());
					return null;
				} else {
					return ifaces;
				}
			} catch (InterruptedException e) {
				s_logger.error("getAllInterfaceNames() :: InterruptedException");
				return ifaces;
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
	}

	public static boolean isUp(String interfaceName) throws KuraException {
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig");

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				if(line.indexOf(interfaceName) > -1 && line.indexOf("mon." + interfaceName) < 0) {
					
					//so the interface is listed - but must also have an IP and netmask to be 'up'
					if(LinuxNetworkUtil.getCurrentIpAddress(interfaceName) == null) {
						return false;
					}
					if(LinuxNetworkUtil.getCurrentNetmask(interfaceName) == null) {
						return false;
					}
					
					return true;
				}
			}

			br.close();
			br = null;

			if (proc.waitFor() != 0) {
				s_logger.error("error executing command --- ifconfig --- exit value = " + proc.exitValue());
				return false;
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		return false;
	}
	
	public static boolean isDhclientRunning(String interfaceName) throws KuraException {
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ps ax");

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				if(line.indexOf(interfaceName) > -1 && line.indexOf("dhclient") > -1) {					
					return true;
				}
			}

			br.close();
			br = null;

			if (proc.waitFor() != 0) {
				s_logger.error("error executing command --- ps ax --- exit value = " + proc.exitValue());
				throw new KuraException(KuraErrorCode.INTERNAL_ERROR, "Unable to check in dhclient is running for " + interfaceName);
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		return false;
	}

	public static String getCurrentIpAddress(String ifaceName) throws KuraException {
		String ipAddress = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while ((line = br.readLine()) != null) {
				if (line.indexOf(ifaceName) > -1) {
					if ((line = br.readLine()) != null) {
						int i = line.indexOf("inet addr:");
						if (i > -1) {
							ipAddress = line.substring(i + 10, line.indexOf(' ', i + 10));
						}
					}
					break;
				}
			}

			if (proc.waitFor() != 0) {
				s_logger.error("getCurrentIpAddress() :: error executing command --- ifconfig " + ifaceName + " --- exit value = " + proc.exitValue());
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		return ipAddress;
	}

	public static String getCurrentNetmask(String ifaceName) throws KuraException {
		String netmask = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while ((line = br.readLine()) != null) {
				if (line.indexOf(ifaceName) > -1) {
					if ((line = br.readLine()) != null) {
						int i = line.indexOf("Mask:");
						if (i > -1) {
							netmask = line.substring(i + 5);
						}
					}
					break;
				}
			}

			if (proc.waitFor() != 0) {
				s_logger.error("getCurrentNetmask() :: error executing command --- ifconfig " + ifaceName + " --- exit value = " + proc.exitValue());
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		return netmask;
	}
	
	public static int getCurrentMtu(String ifaceName) throws KuraException {
		String stringMtu = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while ((line = br.readLine()) != null) {
				if (line.indexOf("MTU:") > -1) {
					stringMtu = line.substring(line.indexOf("MTU:") + 4, line.indexOf("Metric:") - 2);
					break;
				}
			}

			if (proc.waitFor() != 0) {
				s_logger.error("getCurrentMtu() :: error executing command --- ifconfig " + ifaceName + " --- exit value = " + proc.exitValue());
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		if(stringMtu != null) {
			return Integer.parseInt(stringMtu);
		} else {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, "Interface is not up");
		}
	}

	public static String getCurrentBroadcastAddress(String ifaceName) throws KuraException {
		String broadcast = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while ((line = br.readLine()) != null) {
				if (line.indexOf(ifaceName) > -1) {
					if ((line = br.readLine()) != null) {
						int i = line.indexOf("Bcast:");
						if (i > -1) {
							broadcast = line.substring(i + 6, line.indexOf(' ', i + 6));
						}
					}
					break;
				}
			}

			if (proc.waitFor() != 0) {
				s_logger.error("getCurrentBroadcastAddress() :: error executing command --- ifconfig " + ifaceName + " --- exit value = " + proc.exitValue());
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		return broadcast;
	}
	
	public static String getCurrentPtpAddress(String ifaceName) throws KuraException {
		String ptp = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while ((line = br.readLine()) != null) {
				if (line.indexOf(ifaceName) > -1) {
					if ((line = br.readLine()) != null) {
						int i = line.indexOf("P-t-P:");
						if (i > -1) {
							ptp = line.substring(i + 6, line.indexOf(' ', i + 6));
						}
					}
					break;
				}
			}

			if (proc.waitFor() != 0) {
				s_logger.error("getCurrentPtpAddress() :: error executing command --- ifconfig " + ifaceName + " --- exit value = " + proc.exitValue());
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		return ptp;
	}

	public static boolean isLinkUp(String ifaceName) throws KuraException {
		return isLinkUp(getType(ifaceName), ifaceName);
	}
	
	public static boolean isLinkUp(NetInterfaceType ifaceType, String ifaceName) throws KuraException {
		try {
			if(ifaceType == NetInterfaceType.WIFI) {                
				LinkTool linkTool = new IwLinkTool("iw", ifaceName);

				if(linkTool.get()) {
					return linkTool.isLinkDetected();
				} else {
					//throw new KuraException(Kura`ErrorCode.INTERNAL_ERROR, "link tool failed to detect the status of " + ifaceName);
					s_logger.error("link tool failed to detect the status of " + ifaceName);
					return false;
				}
			} else if(ifaceType == NetInterfaceType.ETHERNET) {
				LinkTool linkTool = null;

				String[] tools = new String[]{"/sbin/ethtool", "/usr/sbin/ethtool", "/sbin/mii-tool"};

				for(int i=0; i<tools.length; i++) {
					File tool = new File(tools[i]);
					if(tool.exists()) {
						if(tools[i].indexOf("ethtool") >= 0) {
							linkTool = new EthTool (tools[i], ifaceName);
							break;
						} else {
							linkTool = new MiiTool (ifaceName);
							break;
						}
					}
				}

				if (linkTool != null) {
					if(linkTool.get()) {
						return linkTool.isLinkDetected();
					} else {
						throw new KuraException(KuraErrorCode.INTERNAL_ERROR, "link tool failed to detect the ethernet status of " + ifaceName);
					}
				} else {
					throw new KuraException(KuraErrorCode.INTERNAL_ERROR, "ethtool or mii-tool must be included with the Linux distro");
				}
			} else {
				throw new KuraException(KuraErrorCode.INTERNAL_ERROR, "Unsupported NetInterfaceType: " + ifaceType);
			}
		} catch (Exception e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
	}
	
	public static boolean isAutoConnect(String interfaceName) throws KuraException {
		try {
			NetInterfaceType type = LinuxNetworkUtil.getType(interfaceName);
			if(type != NetInterfaceType.ETHERNET && type != NetInterfaceType.WIFI && type != NetInterfaceType.LOOPBACK) {
				throw new KuraException(KuraErrorCode.INTERNAL_ERROR, "method only supports interfaces of NetInterfaceType ETHERNET, WIFI, or LOOPBACK");
			}
			File interfaceFile = new File("/etc/sysconfig/network-scripts/ifcfg-" + interfaceName);
			if(interfaceFile.exists()) {
				BufferedReader br = new BufferedReader(new FileReader(interfaceFile));
				if(br != null) {
					String line = null;
					while((line = br.readLine()) != null) {
						if(line.contains("ONBOOT=yes")) {
							br.close();
							br = null;
							return true;
						}
					}
					if(br != null) {
						br.close();
						br = null;
					}
				}
			}
			
			return false;
		} catch (Exception e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
	}

	public static String getMacAddress(String ifaceName) throws KuraException {
		String mac = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				int index = line.indexOf("HWaddr ");
				if(index > -1) {
					//found the iface in the list so return
					mac = line.substring(index + 7, line.length()-2);
				}
			}
		} catch (IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		
		return mac;
	}
	
	public static byte[] getMacAddressBytes(String interfaceName) throws KuraException {

		String macAddress = LinuxNetworkUtil.getMacAddress(interfaceName);		

		if(macAddress == null) {
			return new byte[]{0, 0, 0, 0, 0, 0};
		}

		macAddress = macAddress.replaceAll(":","");

		byte[] mac = new byte[6];
        for(int i=0; i<6; i++) {
        	mac[i] = (byte) ((Character.digit(macAddress.charAt(i*2), 16) << 4)
        					+ Character.digit(macAddress.charAt(i*2+1), 16));
        }
        
        return mac;
	}
	
	public static boolean isSupportsMulticast(String interfaceName) throws KuraException {		
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig");

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				if(line.indexOf(interfaceName) > -1 && line.indexOf("mon." + interfaceName) < 0) {
					//eat the next line
					line = br.readLine();
					line = br.readLine();
					
					if(line.contains("MULTICAST")) {
						return true;
					}
				}
			}

			br.close();
			br = null;

			if (proc.waitFor() != 0) {
				s_logger.error("error executing command --- ifconfig --- exit value = " + proc.exitValue());
				return false;
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		return false;
	}

	public static boolean canPing(String ipAddress, int count) throws KuraException {
		Process proc = null;
		try {
			proc = ProcessUtil.exec(new StringBuilder().append("ping -c ").append(count).append(" ").append(ipAddress).toString());
			if(proc.waitFor() == 0) {
				return true;
			} else {
				return false;
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
	}
	
	public static NetInterfaceType getType(String ifaceName) throws KuraException {
		NetInterfaceType ifaceType = NetInterfaceType.UNKNOWN;
		String stringType = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				int index = line.indexOf("Link encap:");
				if(index > -1) {
					StringTokenizer st = new StringTokenizer(line);
					st.nextToken(); //skip iface name
					st.nextToken(); //skip Link
					stringType = st.nextToken();
					stringType = stringType.substring(6).toUpperCase();
					if(stringType.equals("LOCAL")) {
						stringType = "LOOPBACK";
					} else if (stringType.equals("POINT-TO-POINT")) {
						stringType = "MODEM";
					}
					break;
				}
			}
			
			Collection<String> wifiOptions = WifiOptions.getSupportedOptions(ifaceName);
			if (wifiOptions.size() > 0) {
				for (String op : wifiOptions) {
					s_logger.trace("WiFi option supported on " + ifaceName + ": " + op);
				}
				stringType = "WIFI";
			}
			
			File pppFile = new File(NetworkServiceImpl.PPP_PEERS_DIR + ifaceName);
			if(pppFile.exists()) {
			    stringType = "MODEM";
			}
			
			if(ifaceName.matches("^ppp\\d+$")) {
				stringType = "MODEM";
			}
			
			//determine if wifi
			/*
			if("ETHERNET".equals(stringType)) {
				proc = rt.exec("iw dev " + ifaceName + " info");
				int status = proc.waitFor();
				if (status != 0) {
					proc = rt.exec("iwconfig " + ifaceName);				
					br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
	
					while((line = br.readLine()) != null) {
						if(line.contains("IEEE 802.11")) {
							stringType = "WIFI";
							break;
						}
					}				
				} else {
					stringType = "WIFI";
				}
			}
			*/
		} catch (Exception e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		try {
			ifaceType = NetInterfaceType.valueOf(stringType);
		} catch (Exception e) {
			// leave as unknown
		}
		
		return ifaceType;
	}
	
	public static Map<String,String> getEthernetDriver(String interfaceName) throws KuraException{
		Process proc = null;
		BufferedReader br = null;
		
		Map<String, String> driver = new HashMap<String, String>();
		driver.put("name", "unknown");
		driver.put("version", "unkown");
		driver.put("firmware", "unknown");
		
		try {
			//find the location of ethtool
			proc = ProcessUtil.exec("which ethtool");
			
			//get the output
			br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String ethTool = br.readLine();
			if (ethTool != null && ethTool.length() > 0) {
				ethTool = ethTool.replaceAll("\\s", "");
			}
			else {
				s_logger.info("ethtool not found - setting driver to unknown");
				return driver;
			}
			
			//run ethtool
			proc = ProcessUtil.exec(ethTool + " -i " + interfaceName);
			
			//get the output
			br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("driver: ")) {
					driver.put("name", line.substring(line.indexOf(": ") + 1));
				}
				else if (line.startsWith("version: ")) {
					driver.put("version", line.substring(line.indexOf(": ") + 1));
				}
				else if (line.startsWith("firmware-version: ")) {
					driver.put("firmware", line.substring(line.indexOf(": ") + 1));
				}
			}

		} catch (IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		return driver;
	}
	
	public static EnumSet<Capability> getWifiCapabilities(String ifaceName) throws KuraException {
		EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("iwlist " + ifaceName + " auth");
	
			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;
			while((line = br.readLine()) != null) {
				// Remove all whitespace
				String cleanLine = line.replaceAll("\\s", "");
				
				if ("WPA".equals(cleanLine)) {
					capabilities.add(Capability.WPA);
				} else if ("WPA2".equals(cleanLine)) {
					capabilities.add(Capability.RSN);
				} else if ("CIPHER-TKIP".equals(cleanLine)) {
					capabilities.add(Capability.CIPHER_TKIP);
				} else if ("CIPHER-CCMP".equals(cleanLine)) {
					capabilities.add(Capability.CIPHER_CCMP);
					
				// TODO: WEP options don't always seem to be displayed?
				} else if ("WEP-104".equals(cleanLine)) {
					capabilities.add(Capability.CIPHER_WEP104);
				} else if ("WEP-40".equals(cleanLine)) {
					capabilities.add(Capability.CIPHER_WEP40);
				}
				
			}
	
		} catch (IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		return capabilities;
	}
	
	public static WifiMode getWifiMode(String ifaceName) throws KuraException {
		WifiMode mode = WifiMode.UNKNOWN;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("iwconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				int index = line.indexOf("Mode:");
				if(index > -1) {
					s_logger.debug("line: " + line);
					StringTokenizer st = new StringTokenizer(line.substring(index));
					String modeStr = st.nextToken().substring(5);
					if("Managed".equals(modeStr)) {
						mode = WifiMode.INFRA;
					} else if ("Master".equals(modeStr)) {
						mode = WifiMode.MASTER;
					} else if ("Ad-Hoc".equals(modeStr)) {
						mode = WifiMode.ADHOC;
					}
				}
			}
		} catch (IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		
		return mode;
	}
	
	public static long getWifiBitrate(String ifaceName) throws KuraException {
		long bitRate = 0;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("iwconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				int index = line.indexOf("Bit Rate=");
				if(index > -1) {
					s_logger.debug("line: " + line);
					StringTokenizer st = new StringTokenizer(line.substring(index));
					st.nextToken();	// skip 'Bit'
					Double rate = Double.parseDouble(st.nextToken().substring(5));
					int mult = 1;
					
					String unit = st.nextToken();
					if(unit.startsWith("kb")) {
						mult = 1000;
					} else if (unit.startsWith("Mb")) {
						mult = 1000000;
					} else if (unit.startsWith("Gb")) {
						mult = 1000000000;
					}
					
					bitRate = (long) (rate * mult);
				}
			}
		} catch (IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		
		return bitRate;
	}
	
	public static String getSSID(String ifaceName) throws KuraException {
		String ssid = null;
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("iwconfig " + ifaceName);

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				int index = line.indexOf("ESSID:");
				if(index > -1) {
					s_logger.debug("line: " + line);
					String lineSub = line.substring(index);
					StringTokenizer st = new StringTokenizer(lineSub);
					String ssidStr = st.nextToken();
					if(ssidStr.startsWith("\"") && ssidStr.endsWith("\"")) {
						ssid = ssidStr.substring(lineSub.indexOf('"') + 1, lineSub.lastIndexOf('"')); // get value between quotes
					}
				}
			}
		} catch (IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		
		return ssid;
	}
		
	public static void disableInterface(String interfaceName) throws Exception {
		if(interfaceName != null) {
			if (OS_VERSION.equals(KuraConstants.Mini_Gateway.getImageName() + "_" + KuraConstants.Mini_Gateway.getImageVersion())) {
				LinuxProcessUtil.start("ifdown " + interfaceName + "\n");
				LinuxProcessUtil.start("ifconfig " + interfaceName + " down\n");
			} else {
				if (isUp(interfaceName)) {
					LinuxProcessUtil.start("ifdown " + interfaceName + "\n");		
				}
			}
			
			//always leave the Ethernet Controller powered
			powerOnEthernetController(interfaceName);
		}
	}
	
	public static void enableInterface(String interfaceName) throws Exception {
		if(interfaceName != null) {
			if (OS_VERSION.equals(KuraConstants.Mini_Gateway.getImageName() + "_" + KuraConstants.Mini_Gateway.getImageVersion())) {
				LinuxProcessUtil.start("ifconfig " + interfaceName + " up\n");
				LinuxProcessUtil.start("ifup " + interfaceName + "\n");
			} else {
				LinuxProcessUtil.start("ifup " + interfaceName + "\n");						
			}
		}
	}
	
	public static void powerOnEthernetController(String interfaceName) throws KuraException {
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig");

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				if(line.indexOf(interfaceName) > -1 && line.indexOf("mon." + interfaceName) < 0) {
					
					//so the interface is listed - power is already on
					if(LinuxNetworkUtil.getCurrentIpAddress(interfaceName) == null) {
						return;
					}
				}
			}

			br.close();
			br = null;

			if (proc.waitFor() != 0) {
				s_logger.error("error executing command --- ifconfig --- exit value = " + proc.exitValue());
				return;
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
		
		//power the controller since it is not on
		try {
			//start the process
			StringBuilder sb = new StringBuilder().append("ifconfig ").append(interfaceName).append(" 0.0.0.0");
			proc = ProcessUtil.exec(sb.toString());

			if (proc.waitFor() != 0) {
				s_logger.error("error executing command --- " + sb.toString() + " --- exit value = " + proc.exitValue());
				return;
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}
	}
	
	public static boolean isEthernetControllerPowered(String interfaceName) throws KuraException {
		Process proc = null;
		try {
			//start the process
			proc = ProcessUtil.exec("ifconfig");

			//get the output
			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = null;

			while((line = br.readLine()) != null) {
				if(line.indexOf(interfaceName) > -1 && line.indexOf("mon." + interfaceName) < 0) {
					
					//so the interface is listed - power is already on
					return true;
				}
			}

			br.close();
			br = null;

			if (proc.waitFor() != 0) {
				s_logger.error("error executing command --- ifconfig --- exit value = " + proc.exitValue());
			}
		} catch(IOException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		} catch (InterruptedException e) {
			throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
		}
		finally {
			ProcessUtil.destroy(proc);
		}

		return false;
	}
}
