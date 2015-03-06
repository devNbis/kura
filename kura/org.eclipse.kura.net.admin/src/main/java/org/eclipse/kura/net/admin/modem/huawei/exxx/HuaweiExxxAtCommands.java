/**
 * Copyright (c) 2015 nbis and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   nbis
 */
package org.eclipse.kura.net.admin.modem.huawei.exxx;

public enum HuaweiExxxAtCommands {

	at("at\r\n"),
	getModelNumber("at+gmm\r\n"),
    getManufacturer("at+gmi\r\n"),
    getSerialNumber("at+gsn\r\n"),
    getFirmwareVersion("at+gmr\r\n"),
    getSystemInfo("at^sysinfo\r\n"),
    getSystemInfoEx("at^sysinfoex\r\n"),
    getDialmode("at^dialmode\r\n"),
    getSignalStrength("at+csq\r\n"), //AT^HCSQ? -> ME909u-521 LTE LGA Module
    getMobileStationClass("at+cgclass?\r\n"),
	pdpContext("AT+CGDCONT"),
	softReset("AT+CFUN=1,1\r\n"),
	
	//isGpsPowered("at$WPDOM?\r\n"),
	//ME909u-521 LTE LGA Module
	gpsPowerUp("at^WPDGP\r\n"),
	gpsMethodStandalone("at^WPDOM=0\r\n"), //Set the positioning method to Standalone.
	gpsSessionTypeSingle("at^WPDST=0\r\n"), // 0= Set the session type to single	positioning.
	gpsSessionTypeTrac("at^WPDST=1\r\n"),//1= Set the session type to tracking and	positioning
	gpsPositionTimesInterval("at^WPDFR=65535,1\r\n"),//Set the number of positioning times 	and the interval between each 	positioning for the tracking and positioning. If the session is a single positioning, you do not need to set these parameters.
	gpsServiceQuality("at^$WPQOS=255,500\r\n"), //Set the positioning service quality. The first parameter indicates the response time, and the second indicates the horizontal accuracy threshold
	gpsPowerDown("at^WPEND\r\n");
	
	
	private String m_command;
	
	private HuaweiExxxAtCommands(String atCommand) {
		m_command = atCommand;
	}
	
	public String getCommand () {
		return m_command;
	}
}
