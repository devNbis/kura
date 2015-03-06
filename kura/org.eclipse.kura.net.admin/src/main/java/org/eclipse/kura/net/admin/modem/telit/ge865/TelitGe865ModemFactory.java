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

import java.util.Hashtable;

import org.eclipse.kura.net.admin.NetworkConfigurationService;
import org.eclipse.kura.net.admin.modem.CellularModemFactory;
import org.eclipse.kura.net.modem.CellularModem;
import org.eclipse.kura.net.modem.ModemDevice;
import org.eclipse.kura.net.modem.ModemTechnologyType;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.io.ConnectionFactory;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Defines Telit GE865 Modem Factory
 *
 * @author devNbis
 *
 */
public class TelitGe865ModemFactory implements CellularModemFactory {


    private static TelitGe865ModemFactory s_factoryInstance = null;

	private static ModemTechnologyType s_type = ModemTechnologyType.HSDPA;

	private BundleContext s_bundleContext = null;
	private Hashtable<String, TelitGe865> m_modemServices = null;

	private ConnectionFactory m_connectionFactory = null;

	private TelitGe865ModemFactory() {
		s_bundleContext = FrameworkUtil.getBundle(NetworkConfigurationService.class).getBundleContext();

		ServiceTracker<ConnectionFactory, ConnectionFactory> serviceTracker = new ServiceTracker<ConnectionFactory, ConnectionFactory>(s_bundleContext, ConnectionFactory.class, null);
		serviceTracker.open(true);
		m_connectionFactory = serviceTracker.getService();

		m_modemServices = new Hashtable<String, TelitGe865>();
	}

	public static TelitGe865ModemFactory getInstance() {
	    if(s_factoryInstance == null) {
	        s_factoryInstance = new TelitGe865ModemFactory();
	    }
	    return s_factoryInstance;
	}

	@Override
	public CellularModem obtainCellularModemService(ModemDevice modemDevice, String platform) throws Exception {

		String key = modemDevice.getProductName();
		TelitGe865 telitGe865 = m_modemServices.get(key);

		if (telitGe865 == null) {
			telitGe865 = new TelitGe865(modemDevice, platform, m_connectionFactory, s_type);
			this.m_modemServices.put(key, telitGe865);
		}

		return telitGe865;
	}

	@Override
	public Hashtable<String, ? extends CellularModem> getModemServices() {
		return m_modemServices;
	}

	@Override
	public void releaseModemService(String usbPortAddress) {
	    m_modemServices.remove(usbPortAddress);
	}

	@Override
	public ModemTechnologyType getType() {
		return s_type;
	}
}
