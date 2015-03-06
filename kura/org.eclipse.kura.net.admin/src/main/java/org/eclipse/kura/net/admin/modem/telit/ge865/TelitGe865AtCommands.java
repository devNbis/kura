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

package org.eclipse.kura.net.admin.modem.telit.ge865;

/**
 * Defines AT commands for the Telit GE865 modem.
 * 
 * @author devNbis
 *
 */
public enum TelitGe865AtCommands {
	
	getSimStatus("at#qss?\r\n"),
	getSimPinStatus("at+cpin?\r\n"),
	setAutoSimDetection("at#simdet=2\r\n"),
	simulateSimNotInserted("at#simdet=0\r\n"),
	simulateSimInserted("at#simdet=1\r\n"),
	getSmsc("at+csca?\r\n"),
    getMobileStationClass("at+cgclass?\r\n"),
    getRegistrationStatus("at+cgreg?\r\n"),
    getGprsSessionDataVolume("at#gdatavol=1\r\n"),
	pdpContext("AT+CGDCONT");
	
	private String m_command;
	
	private TelitGe865AtCommands(String atCommand) {
		m_command = atCommand;
	}
	
	public String getCommand () {
		return m_command;
	}
}