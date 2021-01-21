package com.multiable.opcq.share.util;

import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.logging.CawLog;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OcfCommonLocal;

public class OcfUtil {

	public static SqlTable getOcfTerms(Long beId) {

		try {
			OcfCommonLocal ocfEJB = JNDILocator.getInstance().lookupEJB(OcfEJB.OcfCommonEJB, OcfCommonLocal.class);
			return ocfEJB.getOcfTerms(beId);
		} catch (Exception e) {
			CawLog.error(e);
		}

		return null;
	}
}
