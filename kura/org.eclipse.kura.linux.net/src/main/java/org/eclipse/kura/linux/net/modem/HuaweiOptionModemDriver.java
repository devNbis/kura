package org.eclipse.kura.linux.net.modem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HuaweiOptionModemDriver extends OptionModemDriver {
	
	private static final Logger s_logger = LoggerFactory.getLogger(HuaweiOptionModemDriver.class);
	private static final String s_vendor = "12d1";
	@SuppressWarnings("unused")
	private final String s_product;

	public HuaweiOptionModemDriver(String product) {
		super(s_vendor, product);
		s_product=product;
	}
	
	public int install() throws Exception {	
		s_logger.info("Installing {} driver for Huawei modem", getName());
		int status = super.install();
		return status;
	}

}
